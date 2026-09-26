package com.clinicaserena.auth.service;

import com.clinicaserena.auth.dto.LoginRequest;
import com.clinicaserena.auth.dto.LoginResponse;
import com.clinicaserena.auth.dto.PatientIdentityResponse;
import com.clinicaserena.auth.entity.CuentaPaciente;
import com.clinicaserena.auth.entity.EstadoCuentaPaciente;
import com.clinicaserena.auth.entity.EstadoPaciente;
import com.clinicaserena.auth.entity.SesionPaciente;
import com.clinicaserena.auth.repository.CuentaPacienteRepository;
import com.clinicaserena.auth.repository.SesionPacienteRepository;
import com.clinicaserena.auth.security.PatientPrincipal;
import com.clinicaserena.auth.security.LoginAttemptGuard;
import com.clinicaserena.auth.security.TokenHasher;
import com.clinicaserena.common.exception.ApiException;
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

@Service
public class PatientAuthService {

    private static final String GENERIC_LOGIN_ERROR = "Credenciales inválidas";
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$7SVF2Ae33PSty.ijWwytpuz43k/lWcvX3vNyNajyRggg7Bg/CjtTO";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final CuentaPacienteRepository cuentaRepository;
    private final SesionPacienteRepository sesionRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptGuard loginAttemptGuard;
    private final long sessionTtlSeconds;

    public PatientAuthService(
            CuentaPacienteRepository cuentaRepository,
            SesionPacienteRepository sesionRepository,
            PasswordEncoder passwordEncoder,
            LoginAttemptGuard loginAttemptGuard,
            @Value("${clinica.auth.session-ttl-seconds:1800}") long sessionTtlSeconds
    ) {
        this.cuentaRepository = cuentaRepository;
        this.sesionRepository = sesionRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptGuard = loginAttemptGuard;
        this.sessionTtlSeconds = sessionTtlSeconds;
    }

    @Transactional
    public LoginResponse login(LoginRequest request, String clientAddress) {
        String email = normalizeEmail(request.email());
        String remoteAddress = normalizeClientAddress(clientAddress);
        String accountKey = email + "|" + remoteAddress;
        if (!loginAttemptGuard.isAllowed(accountKey, remoteAddress)) {
            throw invalidCredentials();
        }

        CuentaPaciente account = cuentaRepository.findByEmailNormalizado(email).orElse(null);
        String passwordHash = account == null ? DUMMY_PASSWORD_HASH : account.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);
        boolean accountIsActive = account != null
                && account.getEstado() == EstadoCuentaPaciente.ACTIVA
                && account.getPaciente().getEstado() == EstadoPaciente.ACTIVO;
        if (!passwordMatches || !accountIsActive) {
            loginAttemptGuard.recordFailure(accountKey, remoteAddress);
            throw invalidCredentials();
        }
        loginAttemptGuard.recordSuccess(accountKey, remoteAddress);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String rawToken = generateToken();
        SesionPaciente session = SesionPaciente.crear(
                UUID.randomUUID().toString(),
                account,
                TokenHasher.sha256(rawToken),
                now.plusSeconds(sessionTtlSeconds),
                now
        );
        sesionRepository.save(session);
        return new LoginResponse(rawToken, "Bearer", sessionTtlSeconds);
    }

    @Transactional(readOnly = true)
    public PatientIdentityResponse me(PatientPrincipal principal) {
        return new PatientIdentityResponse(principal.patientId(), principal.email(), principal.accountStatus());
    }

    @Transactional
    public void logout(PatientPrincipal principal) {
        sesionRepository.findByTokenHash(principal.tokenHash()).ifPresent(session -> {
            if (session.getRevocadaEn() == null) {
                session.revocar(OffsetDateTime.now(ZoneOffset.UTC));
            }
        });
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", GENERIC_LOGIN_ERROR);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeClientAddress(String clientAddress) {
        return clientAddress == null || clientAddress.isBlank() ? "unknown" : clientAddress.trim();
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
