package com.clinicaserena.auth.service;

import com.clinicaserena.auth.dto.LoginRequest;
import com.clinicaserena.auth.dto.UnifiedLoginResponse;
import com.clinicaserena.auth.entity.CuentaPaciente;
import com.clinicaserena.auth.entity.EstadoCuentaPaciente;
import com.clinicaserena.auth.entity.EstadoPaciente;
import com.clinicaserena.auth.entity.SesionPaciente;
import com.clinicaserena.auth.repository.CuentaPacienteRepository;
import com.clinicaserena.auth.repository.SesionPacienteRepository;
import com.clinicaserena.auth.security.LoginAttemptGuard;
import com.clinicaserena.auth.security.TokenHasher;
import com.clinicaserena.common.exception.ApiException;
import com.clinicaserena.staff.entity.CuentaPersonal;
import com.clinicaserena.staff.entity.EstadoCuentaPersonal;
import com.clinicaserena.staff.entity.SesionPersonal;
import com.clinicaserena.staff.repository.CuentaPersonalRepository;
import com.clinicaserena.staff.repository.SesionPersonalRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

/** Resuelve el acceso web sin confiar en un rol enviado por el navegador. */
@Service
public class UnifiedAuthService {

    private static final String GENERIC_LOGIN_ERROR = "Credenciales inválidas";
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$7SVF2Ae33PSty.ijWwytpuz43k/lWcvX3vNyNajyRggg7Bg/CjtTO";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CuentaPacienteRepository patientAccounts;
    private final SesionPacienteRepository patientSessions;
    private final CuentaPersonalRepository staffAccounts;
    private final SesionPersonalRepository staffSessions;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptGuard loginAttemptGuard;
    private final long patientSessionTtlSeconds;
    private final long staffSessionTtlSeconds;

    public UnifiedAuthService(
            CuentaPacienteRepository patientAccounts,
            SesionPacienteRepository patientSessions,
            CuentaPersonalRepository staffAccounts,
            SesionPersonalRepository staffSessions,
            PasswordEncoder passwordEncoder,
            LoginAttemptGuard loginAttemptGuard,
            @Value("${clinica.auth.session-ttl-seconds:1800}") long patientSessionTtlSeconds,
            @Value("${clinica.auth.staff-session-ttl-seconds:1800}") long staffSessionTtlSeconds
    ) {
        this.patientAccounts = patientAccounts;
        this.patientSessions = patientSessions;
        this.staffAccounts = staffAccounts;
        this.staffSessions = staffSessions;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptGuard = loginAttemptGuard;
        this.patientSessionTtlSeconds = patientSessionTtlSeconds;
        this.staffSessionTtlSeconds = staffSessionTtlSeconds;
    }

    @Transactional
    public UnifiedLoginResponse login(LoginRequest request, String clientAddress) {
        String email = normalizeEmail(request.email());
        String remoteAddress = clientAddress == null || clientAddress.isBlank() ? "unknown" : clientAddress.trim();
        String attemptKey = "unified:" + email + "|" + remoteAddress;
        if (!loginAttemptGuard.isAllowed(attemptKey, remoteAddress)) throw invalidCredentials();

        CuentaPaciente patient = patientAccounts.findByEmailNormalizado(email).orElse(null);
        CuentaPersonal staff = staffAccounts.findByEmailNormalizado(email).orElse(null);

        if (patient != null && staff != null) {
            passwordEncoder.matches(request.password(), DUMMY_PASSWORD_HASH);
            loginAttemptGuard.recordFailure(attemptKey, remoteAddress);
            throw invalidCredentials();
        }

        if (patient != null) {
            boolean valid = passwordEncoder.matches(request.password(), patient.getPasswordHash())
                    && patient.getEstado() == EstadoCuentaPaciente.ACTIVA
                    && patient.getEmailVerificadoEn() != null
                    && patient.getPaciente().getEstado() == EstadoPaciente.ACTIVO;
            if (!valid) {
                loginAttemptGuard.recordFailure(attemptKey, remoteAddress);
                throw invalidCredentials();
            }
            loginAttemptGuard.recordSuccess(attemptKey, remoteAddress);
            String token = createPatientSession(patient);
            return new UnifiedLoginResponse(token, "Bearer", patientSessionTtlSeconds, "PACIENTE", "PACIENTE");
        }

        if (staff != null) {
            boolean valid = passwordEncoder.matches(request.password(), staff.getPasswordHash())
                    && staff.getEstado() == EstadoCuentaPersonal.ACTIVA;
            if (!valid) {
                loginAttemptGuard.recordFailure(attemptKey, remoteAddress);
                throw invalidCredentials();
            }
            loginAttemptGuard.recordSuccess(attemptKey, remoteAddress);
            String token = createStaffSession(staff);
            return new UnifiedLoginResponse(token, "Bearer", staffSessionTtlSeconds, "PERSONAL", staff.getRol().name());
        }

        passwordEncoder.matches(request.password(), DUMMY_PASSWORD_HASH);
        loginAttemptGuard.recordFailure(attemptKey, remoteAddress);
        throw invalidCredentials();
    }

    private String createPatientSession(CuentaPaciente account) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String rawToken = randomToken();
        patientSessions.save(SesionPaciente.crear(UUID.randomUUID().toString(), account,
                TokenHasher.sha256(rawToken), now.plusSeconds(patientSessionTtlSeconds), now));
        return rawToken;
    }

    private String createStaffSession(CuentaPersonal account) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String rawToken = randomToken();
        staffSessions.save(SesionPersonal.crear(UUID.randomUUID().toString(), account,
                TokenHasher.sha256(rawToken), now.plusSeconds(staffSessionTtlSeconds), now));
        return rawToken;
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", GENERIC_LOGIN_ERROR);
    }

    private String normalizeEmail(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
