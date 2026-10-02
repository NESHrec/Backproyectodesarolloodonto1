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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.time.Clock;
import java.time.OffsetDateTime;

@Service
public class MedicalScheduleService {

    private final CuentaPersonalRepository accountRepository;
    private final MedicoRepository medicoRepository;
    private final BloqueDisponibilidadRepository blockRepository;
    private final Clock clock;

    public MedicalScheduleService(CuentaPersonalRepository accountRepository,
                                  MedicoRepository medicoRepository,
                                  BloqueDisponibilidadRepository blockRepository,
                                  Clock clock) {
        this.accountRepository = accountRepository;
        this.medicoRepository = medicoRepository;
        this.blockRepository = blockRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ScheduleBlockDto> listOwn(StaffPrincipal principal) {
        String medicoId = requirePractitioner(principal);
        return blockRepository.findByMedicoIdOrderByInicioAsc(medicoId).stream().map(MedicalScheduleService::toDto).toList();
    }

    @Transactional
    public ScheduleBlockDto addOwn(StaffPrincipal principal, ScheduleBlockRequest request) {
        String medicoId = requirePractitioner(principal);
        if (!request.startAt().isBefore(request.endAt())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SCHEDULE_INTERVAL_INVALID",
                    "El inicio debe ser anterior al fin");
        }
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
            return toDto(block);
        } catch (DataIntegrityViolationException collision) {
            throw new ApiException(HttpStatus.CONFLICT, "SCHEDULE_OVERLAP",
                    "El bloque se solapa con otro horario del profesional");
        }
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
