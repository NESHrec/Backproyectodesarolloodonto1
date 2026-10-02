package com.clinicaserena.recepcion.service;

import com.clinicaserena.auditoria.service.BitacoraService;
import com.clinicaserena.auth.entity.Paciente;
import com.clinicaserena.auth.repository.CuentaPacienteRepository;
import com.clinicaserena.auth.repository.PacienteRepository;
import com.clinicaserena.common.exception.ApiException;
import com.clinicaserena.recepcion.dto.AdministrativePatientPageResponse;
import com.clinicaserena.recepcion.dto.AdministrativePatientResponse;
import com.clinicaserena.recepcion.dto.CreateAdministrativePatientRequest;
import com.clinicaserena.recepcion.entity.PacienteAdministrativo;
import com.clinicaserena.recepcion.repository.PacienteAdministrativoRepository;
import com.clinicaserena.recepcion.repository.ReceptionPatientProjection;
import com.clinicaserena.staff.entity.RolPersonal;
import com.clinicaserena.staff.security.StaffPrincipal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class PacienteAdministrativoService {

    private static final int MAX_LIMIT = 100;
    private final PacienteRepository pacienteRepository;
    private final CuentaPacienteRepository patientAccountRepository;
    private final PacienteAdministrativoRepository repository;
    private final BitacoraService bitacoraService;

    public PacienteAdministrativoService(PacienteRepository pacienteRepository,
                                         CuentaPacienteRepository patientAccountRepository,
                                         PacienteAdministrativoRepository repository,
                                         BitacoraService bitacoraService) {
        this.pacienteRepository = pacienteRepository;
        this.patientAccountRepository = patientAccountRepository;
        this.repository = repository;
        this.bitacoraService = bitacoraService;
    }

    @Transactional(readOnly = true)
    public AdministrativePatientPageResponse list(String search, int page, int limit) {
        if (page < 0 || limit < 1 || limit > MAX_LIMIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAGING_INVALID", "La paginación no es válida");
        }
        String normalizedSearch = search == null ? "" : search.trim();
        Page<ReceptionPatientProjection> records = repository.search(normalizedSearch, PageRequest.of(page, limit));
        return new AdministrativePatientPageResponse(records.getContent().stream()
                .map(record -> new AdministrativePatientResponse(record.getPatientId(), record.getFullName(),
                        record.getPhone(), record.getEmail(), record.getRecordType(), record.getPatientAccountLinked(),
                        toOffsetDateTime(record.getCreatedAt())))
                .toList(), page, limit, records.getTotalElements(), records.hasNext());
    }

    @Transactional
    public AdministrativePatientResponse create(StaffPrincipal principal, CreateAdministrativePatientRequest request) {
        if (principal.role() != RolPersonal.RECEPCION) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "No tienes permiso para esta operación");
        }
        String name = normalizeName(request.fullName());
        String phone = request.phone().trim();
        String normalizedPhone = phone.replaceAll("\\D", "");
        if (normalizedPhone.length() < 7 || normalizedPhone.length() > 20) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHONE_INVALID", "El teléfono no es válido");
        }
        String email = normalizeOptionalEmail(request.email());
        if (repository.findByTelefonoNormalizado(normalizedPhone).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "PATIENT_RECORD_EXISTS", "El expediente administrativo ya existe");
        }
        if (email != null && (repository.findByEmailContactoNormalizado(email).isPresent()
                || patientAccountRepository.findByEmailNormalizado(email).isPresent())) {
            throw new ApiException(HttpStatus.CONFLICT, "PATIENT_CONTACT_REQUIRES_MANUAL_LINK",
                    "El correo ya está asociado; la vinculación requiere un procedimiento autorizado");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String patientId = UUID.randomUUID().toString();
        pacienteRepository.saveAndFlush(Paciente.crear(patientId, now));
        PacienteAdministrativo record = PacienteAdministrativo.crear(patientId, name, phone, normalizedPhone,
                email, email, principal.accountId(), now);
        try {
            PacienteAdministrativo saved = repository.saveAndFlush(record);
            bitacoraService.record(principal.accountId(), "PATIENT_ADMINISTRATIVE_RECORD_CREATED",
                    "PACIENTE_ADMINISTRATIVO", patientId, now);
            return new AdministrativePatientResponse(saved.getPacienteId(), saved.getNombreCompleto(), saved.getTelefono(),
                    saved.getEmailContacto(), "EXPEDIENTE_ADMINISTRATIVO", false, saved.getCreadoEn());
        } catch (DataIntegrityViolationException collision) {
            throw new ApiException(HttpStatus.CONFLICT, "PATIENT_RECORD_EXISTS", "El expediente administrativo ya existe");
        }
    }

    private String normalizeName(String value) {
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() < 3) throw new ApiException(HttpStatus.BAD_REQUEST, "NAME_INVALID", "El nombre no es válido");
        return normalized;
    }

    private OffsetDateTime toOffsetDateTime(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    private String normalizeOptionalEmail(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
