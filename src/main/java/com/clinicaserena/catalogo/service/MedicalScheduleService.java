package com.clinicaserena.catalogo.service;

import com.clinicaserena.catalogo.dto.ScheduleBlockDto;
import com.clinicaserena.catalogo.dto.ScheduleBlockRequest;
import com.clinicaserena.catalogo.entity.BloqueDisponibilidad;
import com.clinicaserena.catalogo.entity.Medico;
import com.clinicaserena.catalogo.repository.BloqueDisponibilidadRepository;
import com.clinicaserena.catalogo.repository.MedicoRepository;
import com.clinicaserena.common.exception.ApiException;
import com.clinicaserena.staff.entity.CuentaPersonal;
import com.clinicaserena.staff.entity.EstadoCuentaPersonal;
import com.clinicaserena.staff.entity.RolPersonal;
import com.clinicaserena.staff.repository.CuentaPersonalRepository;
import com.clinicaserena.staff.security.StaffPrincipal;
import com.clinicaserena.auditoria.service.BitacoraService;
import com.clinicaserena.common.time.BusinessTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.Duration;

@Service
public class MedicalScheduleService {

    private final CuentaPersonalRepository accountRepository;
    private final MedicoRepository medicoRepository;
    private final BloqueDisponibilidadRepository blockRepository;
    private final Clock clock;
    private final BitacoraService audit;

    public MedicalScheduleService(CuentaPersonalRepository accountRepository,
                                  MedicoRepository medicoRepository,
                                  BloqueDisponibilidadRepository blockRepository,
                                  Clock clock, BitacoraService audit) {
        this.accountRepository = accountRepository;
        this.medicoRepository = medicoRepository;
        this.blockRepository = blockRepository;
        this.clock = clock;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ScheduleBlockDto> listOwn(StaffPrincipal principal) {
        String medicoId = requirePractitioner(principal);
        return blockRepository.findByMedicoIdOrderByInicioAsc(medicoId).stream().map(MedicalScheduleService::toDto).toList();
    }

    @Transactional
    public ScheduleBlockDto addOwn(StaffPrincipal principal, ScheduleBlockRequest request) {
        String medicoId = requirePractitioner(principal);
        validateInterval(request);
        if (request.startAt().isBefore(OffsetDateTime.now(clock))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SCHEDULE_START_IN_PAST",
                    "No se puede crear un bloque cuyo inicio ya pasó");
        }

        // Bloquear al médico serializa las comprobaciones de solapamiento del mismo profesional.
        Medico medico = medicoRepository.findByIdForUpdate(medicoId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "PRACTITIONER_NOT_FOUND",
                        "El profesional vinculado ya no existe"));
        if (blockRepository.existsOverlapping(medicoId, request.startAt(), request.endAt())) {
            throw new ApiException(HttpStatus.CONFLICT, "SCHEDULE_OVERLAP",
                    "El bloque se solapa con otro horario del profesional");
        }
        try {
            BloqueDisponibilidad block = blockRepository.saveAndFlush(BloqueDisponibilidad.crear(
                    UUID.randomUUID().toString(), medico, request.startAt(), request.endAt()));
            audit.record(principal, "SCHEDULE_BLOCK_CREATED", "BLOQUE_DISPONIBILIDAD", block.getId(), OffsetDateTime.now(clock));
            return toDto(block);
        } catch (DataIntegrityViolationException collision) {
            throw new ApiException(HttpStatus.CONFLICT, "SCHEDULE_OVERLAP",
                    "El bloque se solapa con otro horario del profesional");
        }
    }

    @Transactional
    public ScheduleBlockDto updateOwn(StaffPrincipal principal, String blockId, ScheduleBlockRequest request) {
        String medicoId = requirePractitioner(principal);
        validateInterval(request);
        medicoRepository.findByIdForUpdate(medicoId).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                "PRACTITIONER_NOT_FOUND", "El profesional vinculado ya no existe"));
        BloqueDisponibilidad block = blockRepository.findByIdForUpdate(blockId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SCHEDULE_BLOCK_NOT_FOUND", "El bloque no existe"));
        requireOwner(block, medicoId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (block.isRetirado()) throw new ApiException(HttpStatus.CONFLICT, "SCHEDULE_BLOCK_RETIRED", "El bloque ya fue retirado");
        if (!block.getInicio().isAfter(now)) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "SCHEDULE_BLOCK_NOT_FUTURE", "Solo se pueden editar bloques futuros");
        if (!request.startAt().isAfter(now)) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "SCHEDULE_START_IN_PAST", "El nuevo inicio debe ser posterior a la hora del servidor");
        if (blockRepository.hasAnyAppointment(blockId)) throw new ApiException(HttpStatus.CONFLICT,
                "SCHEDULE_BLOCK_HAS_APPOINTMENTS", "El bloque tiene citas asociadas y no puede editarse");
        if (blockRepository.existsOverlappingExcluding(medicoId, blockId, request.startAt(), request.endAt()))
            throw new ApiException(HttpStatus.CONFLICT, "SCHEDULE_OVERLAP", "El bloque se solapa con otro horario del profesional");
        block.reprogramar(request.startAt(), request.endAt());
        blockRepository.saveAndFlush(block);
        audit.record(principal, "SCHEDULE_BLOCK_UPDATED", "BLOQUE_DISPONIBILIDAD", blockId, now);
        return toDto(block);
    }

    @Transactional
    public void retireOwn(StaffPrincipal principal, String blockId) {
        String medicoId = requirePractitioner(principal);
        medicoRepository.findByIdForUpdate(medicoId).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                "PRACTITIONER_NOT_FOUND", "El profesional vinculado ya no existe"));
        BloqueDisponibilidad block = blockRepository.findByIdForUpdate(blockId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SCHEDULE_BLOCK_NOT_FOUND", "El bloque no existe"));
        requireOwner(block, medicoId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (block.isRetirado()) return;
        if (!block.getInicio().isAfter(now)) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "SCHEDULE_BLOCK_NOT_FUTURE", "Solo se pueden retirar bloques futuros");
        block.retirar(principal.accountId(), now);
        blockRepository.saveAndFlush(block);
        audit.record(principal, "SCHEDULE_BLOCK_RETIRED", "BLOQUE_DISPONIBILIDAD", blockId, now);
    }

    private void validateInterval(ScheduleBlockRequest request) {
        if (!request.startAt().isBefore(request.endAt())) throw new ApiException(HttpStatus.BAD_REQUEST,
                "SCHEDULE_INTERVAL_INVALID", "El inicio debe ser anterior al fin");
        Duration duration = Duration.between(request.startAt(), request.endAt());
        if (duration.toMinutes() < 15 || duration.toHours() > 12) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "SCHEDULE_DURATION_INVALID", "La duracion debe estar entre 15 minutos y 12 horas");
        // OffsetDateTime representa un instante inequívoco. La presentación y las reglas de
        // hora de pared se normalizan siempre con BusinessTime.ZONE (America/Guatemala).
        request.startAt().atZoneSameInstant(BusinessTime.ZONE);
        request.endAt().atZoneSameInstant(BusinessTime.ZONE);
    }

    private void requireOwner(BloqueDisponibilidad block, String medicoId) {
        if (!block.getMedico().getId().equals(medicoId)) throw new ApiException(HttpStatus.FORBIDDEN,
                "SCHEDULE_BLOCK_NOT_OWNED", "Solo el medico propietario puede modificar el bloque");
    }

    private String requirePractitioner(StaffPrincipal principal) {
        CuentaPersonal account = accountRepository.findById(principal.accountId())
                .filter(found -> found.getEstado() == EstadoCuentaPersonal.ACTIVA)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                        "Autenticación requerida"));
        if (account.getRol() != RolPersonal.MEDICO) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "No tienes permiso para esta operación");
        }
        if (account.getMedicoId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PRACTITIONER_LINK_REQUIRED",
                    "La cuenta médica aún no está vinculada a un profesional");
        }
        return account.getMedicoId();
    }

    private static ScheduleBlockDto toDto(BloqueDisponibilidad block) {
        return new ScheduleBlockDto(block.getId(), block.getMedico().getId(), block.getInicio(),
                block.getFin(), block.isDisponible());
    }
}
