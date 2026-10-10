package com.clinicaserena.citas.service;

import com.clinicaserena.catalogo.entity.BloqueDisponibilidad;
import com.clinicaserena.catalogo.repository.BloqueDisponibilidadRepository;
import com.clinicaserena.auditoria.service.BitacoraService;
import com.clinicaserena.auth.security.PatientPrincipal;
import com.clinicaserena.citas.dto.CitaDto;
import com.clinicaserena.citas.dto.CrearCitaRequest;
import com.clinicaserena.citas.entity.Cita;
import com.clinicaserena.citas.entity.EstadoCita;
import com.clinicaserena.citas.repository.CitaRepository;
import com.clinicaserena.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CitaService {

    private final BloqueDisponibilidadRepository bloqueRepository;
    private final CitaRepository citaRepository;
    private final Clock clock;
    private final BitacoraService audit;
    public CitaService(BloqueDisponibilidadRepository bloqueRepository, CitaRepository citaRepository, Clock clock,
                       BitacoraService audit) {
        this.bloqueRepository = bloqueRepository;
        this.citaRepository = citaRepository;
        this.clock = clock;
        this.audit = audit;
    }

    @Transactional
    public CitaDto reservar(PatientPrincipal principal, CrearCitaRequest request) {
        CitaDto appointment = reservarPersistida(principal.patientId(), request);
        audit.recordPatient(principal, "APPOINTMENT_BOOKED", "CITA", appointment.id(), appointment.createdAt());
        return appointment;
    }

    /**
     * Punto de entrada para una futura capa autenticada. pacienteId debe provenir del principal validado.
     */
    @Transactional
    public CitaDto reservar(String pacienteId, CrearCitaRequest request) {
        return reservarPersistida(pacienteId, request);
    }

    private CitaDto reservarPersistida(String pacienteId, CrearCitaRequest request) {
        validarIdentificador(pacienteId, "PATIENT_ID_INVALID", "La identidad del paciente no es válida");
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_APPOINTMENT", "Los datos de la cita son obligatorios");
        }
        validarIdentificador(request.practitionerId(), "PRACTITIONER_ID_INVALID", "El profesional es obligatorio");
        validarIdentificador(request.specialtyId(), "SPECIALTY_ID_INVALID", "La especialidad es obligatoria");
        if (request.scheduledAt() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SCHEDULED_AT_INVALID", "La fecha de la cita es obligatoria");
        }
        if (request.notes() != null && request.notes().length() > 1000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOTES_TOO_LONG", "Las notas no pueden exceder 1000 caracteres");
        }

        BloqueDisponibilidad bloque = bloqueRepository.findByMedicoAndInicioForUpdate(
                        request.practitionerId(), request.scheduledAt())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SLOT_NOT_FOUND", "El bloque no existe"));

        OffsetDateTime ahora = OffsetDateTime.now(clock);
        if (bloque.getInicio().isBefore(ahora)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SLOT_IN_PAST",
                    "No se puede reservar un bloque cuyo inicio ya pasó");
        }

        if (!bloque.getMedico().getId().equals(request.practitionerId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SLOT_PRACTITIONER_MISMATCH", "El bloque no pertenece al profesional indicado");
        }
        if (!bloque.getMedico().getEspecialidad().getId().equals(request.specialtyId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SPECIALTY_PRACTITIONER_MISMATCH", "La especialidad no corresponde al profesional indicado");
        }
        if (!bloque.isDisponible()
                || citaRepository.existsByBloqueIdAndEstadoNot(bloque.getId(), EstadoCita.CANCELADA)) {
            throw new ApiException(HttpStatus.CONFLICT, "SLOT_NOT_AVAILABLE", "El bloque ya no está disponible");
        }

        Cita cita = new Cita(UUID.randomUUID().toString(), pacienteId, bloque, normalizarNotas(request.notes()), ahora);
        bloque.reservar();
        return toDto(citaRepository.saveAndFlush(cita));
    }

    @Transactional(readOnly = true)
    public List<CitaDto> listarDelPaciente(String pacienteId, EstadoCita estado) {
        validarIdentificador(pacienteId, "PATIENT_ID_INVALID", "La identidad del paciente no es válida");
        List<Cita> citas = estado == null
                ? citaRepository.findByPacienteIdOrderByProgramadaEnDesc(pacienteId)
                : citaRepository.findByPacienteIdAndEstadoOrderByProgramadaEnDesc(pacienteId, estado);
        return citas.stream().map(this::toDto).toList();
    }

    /**
     * Operación de dominio para una futura capa autenticada; no está expuesta por HTTP en esta fase.
     */
    @Transactional
    public CitaDto cancelar(String pacienteId, String citaId) {
        validarIdentificador(pacienteId, "PATIENT_ID_INVALID", "La identidad del paciente no es válida");
        validarIdentificador(citaId, "APPOINTMENT_ID_INVALID", "La cita es obligatoria");

        Cita cita = citaRepository.findById(citaId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND", "La cita no existe"));
        if (!cita.getPacienteId().equals(pacienteId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND", "La cita no existe");
        }

        BloqueDisponibilidad bloque = bloqueRepository.findByIdForUpdate(cita.getBloque().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SLOT_NOT_FOUND", "El bloque no existe"));
        OffsetDateTime ahora = OffsetDateTime.now(clock);
        if (cita.getEstado() == EstadoCita.CANCELADA) {
            throw new ApiException(HttpStatus.CONFLICT, "APPOINTMENT_ALREADY_CANCELLED", "La cita ya está cancelada");
        }
        if (cita.getEstado() == EstadoCita.COMPLETADA || !cita.getProgramadaEn().isAfter(ahora)) {
            throw new ApiException(HttpStatus.CONFLICT, "APPOINTMENT_NOT_CANCELLABLE", "La cita ya no puede cancelarse");
        }

        cita.cancelar(ahora);
        bloque.liberar();
        return toDto(citaRepository.saveAndFlush(cita));
    }

    private void validarIdentificador(String valor, String codigo, String mensaje) {
        if (valor == null || valor.isBlank() || valor.length() > 36) {
            throw new ApiException(HttpStatus.BAD_REQUEST, codigo, mensaje);
        }
    }

    private String normalizarNotas(String notas) {
        return notas == null || notas.isBlank() ? null : notas.trim();
    }

    private CitaDto toDto(Cita cita) {
        return new CitaDto(
                cita.getId(), cita.getPacienteId(), cita.getMedico().getId(), cita.getEspecialidad().getId(),
                cita.getProgramadaEn(), cita.getEstado(), cita.getNotas(), cita.getMontoCentavos(),
                cita.getCreadaEn(), cita.getActualizadaEn()
        );
    }
}
