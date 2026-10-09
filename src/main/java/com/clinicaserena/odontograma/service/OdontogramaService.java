package com.clinicaserena.odontograma.service;

import com.clinicaserena.auditoria.service.BitacoraService;
import com.clinicaserena.auth.entity.CuentaPaciente;
import com.clinicaserena.auth.repository.CuentaPacienteRepository;
import com.clinicaserena.auth.repository.PacienteRepository;
import com.clinicaserena.citas.entity.Cita;
import com.clinicaserena.citas.entity.EstadoCita;
import com.clinicaserena.citas.repository.CitaRepository;
import com.clinicaserena.common.exception.ApiException;
import com.clinicaserena.odontograma.dto.DentalObservationPageResponse;
import com.clinicaserena.odontograma.dto.DentalObservationResponse;
import com.clinicaserena.odontograma.dto.RecordDentalObservationRequest;
import com.clinicaserena.odontograma.entity.ObservacionOdontograma;
import com.clinicaserena.odontograma.repository.ObservacionOdontogramaRepository;
import com.clinicaserena.staff.entity.CuentaPersonal;
import com.clinicaserena.staff.entity.EstadoCuentaPersonal;
import com.clinicaserena.staff.entity.RolPersonal;
import com.clinicaserena.staff.repository.CuentaPersonalRepository;
import com.clinicaserena.staff.security.StaffPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class OdontogramaService {

    private static final Set<Integer> VALID_TEETH = Set.of(
            11,12,13,14,15,16,17,18,21,22,23,24,25,26,27,28,
            31,32,33,34,35,36,37,38,41,42,43,44,45,46,47,48,
            51,52,53,54,55,61,62,63,64,65,71,72,73,74,75,81,82,83,84,85);
    private static final Set<String> VALID_SURFACES = Set.of(
            "MESIAL", "DISTAL", "VESTIBULAR", "LINGUAL", "PALATINA", "OCLUSAL", "INCISAL");
    private final CuentaPersonalRepository staffRepository;
    private final CitaRepository citaRepository;
    private final PacienteRepository pacienteRepository;
    private final CuentaPacienteRepository patientAccountRepository;
    private final ObservacionOdontogramaRepository repository;
    private final Clock clock;
    private final BitacoraService audit;

    public OdontogramaService(CuentaPersonalRepository staffRepository, CitaRepository citaRepository,
                              PacienteRepository pacienteRepository, CuentaPacienteRepository patientAccountRepository,
                              ObservacionOdontogramaRepository repository, Clock clock, BitacoraService audit) {
        this.staffRepository = staffRepository;
        this.citaRepository = citaRepository;
        this.pacienteRepository = pacienteRepository;
        this.patientAccountRepository = patientAccountRepository;
        this.repository = repository;
        this.clock = clock;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public DentalObservationPageResponse listForPatient(StaffPrincipal principal, String patientId) {
        String practitionerId = requirePractitioner(principal);
        if (!pacienteRepository.existsById(patientId)) throw notFound();
        if (citaRepository.findFirstByMedico_IdAndPacienteIdOrderByProgramadaEnDesc(practitionerId, patientId).isEmpty()) {
            throw notFound();
        }
        String name = patientAccountRepository.findByPacienteIdIn(List.of(patientId)).stream()
                .findFirst().map(CuentaPaciente::getNombreCompleto).orElse(null);
        return new DentalObservationPageResponse(patientId, name,
                repository.findByPacienteIdOrderByRegistradaEnDesc(patientId).stream().map(this::toResponse).toList());
    }

    @Transactional
    public DentalObservationResponse record(StaffPrincipal principal, String appointmentId,
                                            RecordDentalObservationRequest request) {
        String practitionerId = requirePractitioner(principal);
        if (!VALID_TEETH.contains(request.toothNumber())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOOTH_INVALID", "La pieza dental no es válida");
        }
        String observation = request.observation().trim();
        String surface = request.surface().trim().toUpperCase(java.util.Locale.ROOT);
        if (!VALID_SURFACES.contains(surface)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SURFACE_INVALID", "La superficie dental no es válida");
        }
        if (observation.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OBSERVATION_INVALID", "La observación es obligatoria");
        }
        // La misma fila queda bloqueada hasta el INSERT: una atención concurrente
        // no puede completar la cita entre estas comprobaciones y la observación.
        Cita cita = citaRepository.findByIdForUpdate(appointmentId)
                .filter(found -> found.getMedico().getId().equals(practitionerId))
                .orElseThrow(OdontogramaService::notFound);
        if (!pacienteRepository.existsById(cita.getPacienteId())) {
            throw new ApiException(HttpStatus.CONFLICT, "PATIENT_RECORD_UNAVAILABLE", "La cita no está asociada a un paciente registrado");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (cita.getEstado() == EstadoCita.CANCELADA || cita.getEstado() == EstadoCita.COMPLETADA) {
            throw new ApiException(HttpStatus.CONFLICT, "APPOINTMENT_NOT_DOCUMENTABLE", "La cita no admite observaciones nuevas");
        }
        if (cita.getProgramadaEn().isAfter(now)) {
            throw new ApiException(HttpStatus.CONFLICT, "APPOINTMENT_NOT_STARTED", "La cita aún no ha comenzado");
        }
        if (cita.getLlegadaEn() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "ARRIVAL_NOT_REGISTERED", "Recepción debe registrar la llegada antes de documentar");
        }
        ObservacionOdontograma saved = repository.saveAndFlush(ObservacionOdontograma.registrar(UUID.randomUUID().toString(),
                cita.getPacienteId(), cita.getId(), practitionerId, principal.accountId(),
                (short) request.toothNumber(), surface, observation, now));
        audit.record(principal, "DENTAL_OBSERVATION_RECORDED", "OBSERVACION_ODONTOGRAMA", saved.getId(), now);
        return toResponse(saved);
    }

    private String requirePractitioner(StaffPrincipal principal) {
        CuentaPersonal account = staffRepository.findById(principal.accountId())
                .filter(found -> found.getEstado() == EstadoCuentaPersonal.ACTIVA)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Autenticación requerida"));
        if (account.getRol() != RolPersonal.MEDICO || account.getMedicoId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "No tienes permiso para esta operación");
        }
        return account.getMedicoId();
    }

    private DentalObservationResponse toResponse(ObservacionOdontograma record) {
        return new DentalObservationResponse(record.getId(), record.getPacienteId(), record.getCitaId(),
                record.getMedicoId(), record.getAutorPersonalId(), record.getPiezaDental(), record.getSuperficie(), record.getObservacion(),
                record.getRegistradaEn());
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PATIENT_NOT_FOUND", "El paciente no existe o no pertenece a tu agenda");
    }
}
