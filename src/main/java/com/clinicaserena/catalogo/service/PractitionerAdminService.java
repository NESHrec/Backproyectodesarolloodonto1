package com.clinicaserena.catalogo.service;

import com.clinicaserena.auditoria.service.BitacoraService;
import com.clinicaserena.catalogo.dto.PractitionerDto;
import com.clinicaserena.catalogo.dto.UpsertPractitionerRequest;
import com.clinicaserena.catalogo.entity.Especialidad;
import com.clinicaserena.catalogo.entity.Medico;
import com.clinicaserena.catalogo.mapper.CatalogoMapper;
import com.clinicaserena.catalogo.repository.EspecialidadRepository;
import com.clinicaserena.catalogo.repository.MedicoRepository;
import com.clinicaserena.common.exception.ApiException;
import com.clinicaserena.staff.security.StaffPrincipal;
import com.clinicaserena.staff.entity.EstadoCuentaPersonal;
import com.clinicaserena.staff.entity.RolPersonal;
import com.clinicaserena.staff.repository.CuentaPersonalRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.Locale;

@Service
public class PractitionerAdminService {
    private final MedicoRepository practitioners;
    private final EspecialidadRepository specialties;
    private final CatalogoMapper mapper;
    private final PractitionerPhotoStorage photos;
    private final BitacoraService audit;
    private final CuentaPersonalRepository staffAccounts;

    public PractitionerAdminService(MedicoRepository practitioners, EspecialidadRepository specialties,
                                    CatalogoMapper mapper, PractitionerPhotoStorage photos, BitacoraService audit,
                                    CuentaPersonalRepository staffAccounts) {
        this.practitioners = practitioners; this.specialties = specialties; this.mapper = mapper; this.photos = photos; this.audit = audit; this.staffAccounts = staffAccounts;
    }

    @Transactional(readOnly = true)
    public List<PractitionerDto> list() { return practitioners.findAllByOrderByNombreCompletoAsc().stream().map(mapper::toDto).toList(); }

    @Transactional
    public PractitionerDto create(StaffPrincipal principal, UpsertPractitionerRequest request) {
        String name = clean(request.fullName(), 160, "PRACTITIONER_NAME_INVALID");
        String license = normalizeLicense(request.licenseNumber());
        if (practitioners.existsByNormalizedLicense(license, null)) throw conflict();
        Especialidad specialty = specialty(request.specialtyId());
        Medico saved;
        try { saved = practitioners.saveAndFlush(Medico.crear(UUID.randomUUID().toString(), name, specialty, license)); }
        catch (DataIntegrityViolationException duplicate) { throw conflict(); }
        record(principal, "PRACTITIONER_CREATED", saved.getId());
        return mapper.toDto(saved);
    }

    @Transactional
    public PractitionerDto update(StaffPrincipal principal, String id, UpsertPractitionerRequest request) {
        Medico practitioner = locked(id);
        String name = clean(request.fullName(), 160, "PRACTITIONER_NAME_INVALID");
        String license = normalizeLicense(request.licenseNumber());
        if (practitioners.existsByNormalizedLicense(license, id)) throw conflict();
        practitioner.actualizar(name, specialty(request.specialtyId()), license);
        try { practitioners.flush(); } catch (DataIntegrityViolationException duplicate) { throw conflict(); }
        record(principal, "PRACTITIONER_UPDATED", id);
        return mapper.toDto(practitioner);
    }

    @Transactional
    public PractitionerDto status(StaffPrincipal principal, String id, boolean active) {
        Medico practitioner = locked(id);
        if (!active && staffAccounts.existsByMedicoIdAndRolAndEstado(id, RolPersonal.MEDICO, EstadoCuentaPersonal.ACTIVA))
            throw new ApiException(HttpStatus.CONFLICT, "PRACTITIONER_HAS_ACTIVE_ACCOUNT", "Desvincula primero la cuenta MEDICO activa asociada");
        practitioner.cambiarEstado(active); practitioners.flush();
        record(principal, active ? "PRACTITIONER_ENABLED" : "PRACTITIONER_DISABLED", id);
        return mapper.toDto(practitioner);
    }

    @Transactional
    public PractitionerDto replacePhoto(StaffPrincipal principal, String id, MultipartFile file) {
        Medico practitioner = locked(id);
        PractitionerPhotoStorage.StoredPhoto stored = photos.store(file);
        deleteNewOnRollback(stored.key());
        String previous = practitioner.getFotoClave();
        try {
            practitioner.asignarFoto(stored.key(), stored.contentType()); practitioners.flush();
            record(principal, "PRACTITIONER_PHOTO_REPLACED", id); deleteAfterCommit(previous);
            return mapper.toDto(practitioner);
        } catch (RuntimeException failure) { throw failure; }
    }

    @Transactional
    public PractitionerDto removePhoto(StaffPrincipal principal, String id) {
        Medico practitioner = locked(id); String previous = practitioner.getFotoClave();
        practitioner.quitarFoto(); practitioners.flush(); record(principal, "PRACTITIONER_PHOTO_REMOVED", id); deleteAfterCommit(previous);
        return mapper.toDto(practitioner);
    }

    private void deleteAfterCommit(String key) {
        if (key == null) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { if (!practitioners.existsByFotoClave(key)) photos.delete(key); }
        });
    }
    private void deleteNewOnRollback(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) { if (status != STATUS_COMMITTED) photos.delete(key); }
        });
    }
    private Medico locked(String id) { return practitioners.findByIdForUpdate(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRACTITIONER_NOT_FOUND", "El profesional no existe")); }
    private Especialidad specialty(String id) { return specialties.findById(id.trim()).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "SPECIALTY_NOT_FOUND", "La especialidad no existe")); }
    private static String clean(String value, int max, String code) { String cleaned = value == null ? "" : value.trim().replaceAll("\\s+", " "); if (cleaned.isBlank() || cleaned.length() > max) throw new ApiException(HttpStatus.BAD_REQUEST, code, "Dato de profesional inválido"); return cleaned; }
    private static ApiException conflict() { return new ApiException(HttpStatus.CONFLICT, "PRACTITIONER_LICENSE_DUPLICATE", "Ya existe un profesional con ese colegiado"); }
    static String normalizeLicense(String value) {
        String normalized = clean(value, 50, "PRACTITIONER_LICENSE_INVALID")
                .replaceAll("[\\t\\n\\u000B\\f\\r ]+", " ").toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "PRACTITIONER_LICENSE_INVALID", "Dato de profesional inválido");
        return normalized;
    }
    private void record(StaffPrincipal p, String action, String id) { audit.record(p, action, "MEDICO", id, OffsetDateTime.now(ZoneOffset.UTC)); }
}
