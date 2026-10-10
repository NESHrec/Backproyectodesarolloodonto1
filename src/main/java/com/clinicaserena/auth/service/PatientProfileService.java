package com.clinicaserena.auth.service;

import com.clinicaserena.auth.dto.PatientProfileResponse;
import com.clinicaserena.auth.dto.UpdatePatientProfileRequest;
import com.clinicaserena.auth.entity.CuentaPaciente;
import com.clinicaserena.auth.repository.CuentaPacienteRepository;
import com.clinicaserena.auth.security.PatientPrincipal;
import com.clinicaserena.auditoria.service.BitacoraService;
import com.clinicaserena.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class PatientProfileService {

    private final CuentaPacienteRepository accountRepository;
    private final BitacoraService audit;

    public PatientProfileService(CuentaPacienteRepository accountRepository, BitacoraService audit) {
        this.accountRepository = accountRepository;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PatientProfileResponse getOwn(PatientPrincipal principal) {
        return toResponse(requireOwnAccount(principal));
    }

    @Transactional
    public PatientProfileResponse updateOwn(PatientPrincipal principal, UpdatePatientProfileRequest request) {
        String fullName = request.fullName().trim();
        if (fullName.length() < 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "El nombre completo debe tener al menos 2 caracteres no vacíos");
        }
        CuentaPaciente account = accountRepository.findByIdForUpdate(principal.accountId())
                .filter(found -> found.getPaciente().getId().equals(principal.patientId()))
                .orElseThrow(this::unauthenticated);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        account.cambiarNombreCompleto(fullName, now);
        audit.recordPatient(principal, "PATIENT_PROFILE_UPDATED", "CUENTA_PACIENTE", account.getId(), now);
        return toResponse(account);
    }

    private CuentaPaciente requireOwnAccount(PatientPrincipal principal) {
        return accountRepository.findById(principal.accountId())
                .filter(account -> account.getPaciente().getId().equals(principal.patientId()))
                .orElseThrow(this::unauthenticated);
    }

    private PatientProfileResponse toResponse(CuentaPaciente account) {
        return new PatientProfileResponse(
                account.getPaciente().getId(),
                account.getNombreCompleto(),
                account.getEmailNormalizado(),
                account.getEstado().name(),
                account.getPaciente().getCreadoEn());
    }

    private ApiException unauthenticated() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Autenticación requerida");
    }
}
