package com.clinicaserena.staff.service;

import com.clinicaserena.auth.dto.LoginResponse;
import com.clinicaserena.auth.security.LoginAttemptGuard;
import com.clinicaserena.auth.security.TokenHasher;
import com.clinicaserena.common.exception.ApiException;
import com.clinicaserena.staff.dto.CreateStaffAccountRequest;
import com.clinicaserena.staff.dto.StaffAccountResponse;
import com.clinicaserena.staff.dto.StaffIdentityResponse;
import com.clinicaserena.staff.dto.StaffLoginRequest;
import com.clinicaserena.staff.entity.CuentaPersonal;
import com.clinicaserena.staff.entity.EstadoCuentaPersonal;
import com.clinicaserena.staff.entity.EstadoVinculacionMedico;
import com.clinicaserena.staff.entity.RolPersonal;
import com.clinicaserena.staff.entity.SesionPersonal;
import com.clinicaserena.staff.repository.CuentaPersonalRepository;
import com.clinicaserena.staff.repository.SesionPersonalRepository;
import com.clinicaserena.staff.security.StaffPrincipal;
import com.clinicaserena.auditoria.service.BitacoraService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
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
public class StaffAuthService {

    private static final String GENERIC_LOGIN_ERROR = "Credenciales inválidas";
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$7SVF2Ae33PSty.ijWwytpuz43k/lWcvX3vNyNajyRggg7Bg/CjtTO";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CuentaPersonalRepository accountRepository;
    private final SesionPersonalRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptGuard loginAttemptGuard;
    private final long sessionTtlSeconds;
    private final BitacoraService audit;

    public StaffAuthService(CuentaPersonalRepository accountRepository,
                            SesionPersonalRepository sessionRepository,
                            PasswordEncoder passwordEncoder,
                            LoginAttemptGuard loginAttemptGuard,
                            @Value("${clinica.auth.staff-session-ttl-seconds:1800}") long sessionTtlSeconds,
                            BitacoraService audit) {
        this.accountRepository = accountRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptGuard = loginAttemptGuard;
        this.sessionTtlSeconds = sessionTtlSeconds;
        this.audit = audit;
    }

    @Transactional
    public LoginResponse login(StaffLoginRequest request, String clientAddress) {
        String email = normalizeEmail(request.email());
        String address = clientAddress == null || clientAddress.isBlank() ? "unknown" : clientAddress.trim();
        String key = "staff:" + email + "|" + address;
        if (!loginAttemptGuard.isAllowed(key, address)) throw invalidCredentials();

        CuentaPersonal account = accountRepository.findByEmailNormalizado(email).orElse(null);
        boolean passwordMatches = passwordEncoder.matches(request.password(),
                account == null ? DUMMY_PASSWORD_HASH : account.getPasswordHash());
        boolean active = account != null && account.getEstado() == EstadoCuentaPersonal.ACTIVA;
        if (!passwordMatches || !active) {
            loginAttemptGuard.recordFailure(key, address);
            throw invalidCredentials();
        }
        loginAttemptGuard.recordSuccess(key, address);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String rawToken = randomToken();
        sessionRepository.save(SesionPersonal.crear(UUID.randomUUID().toString(), account,
                TokenHasher.sha256(rawToken), now.plusSeconds(sessionTtlSeconds), now));
        return new LoginResponse(rawToken, "Bearer", sessionTtlSeconds);
    }

    @Transactional(readOnly = true)
    public StaffIdentityResponse me(StaffPrincipal principal) {
        CuentaPersonal account = accountRepository.findById(principal.accountId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Autenticación requerida"));
        return new StaffIdentityResponse(account.getId(), account.getEmailNormalizado(), account.getNombreCompleto(),
                account.getRol(), EstadoVinculacionMedico.de(account), account.getMedicoId());
    }

    @Transactional
    public void logout(StaffPrincipal principal) {
        sessionRepository.findByTokenHash(principal.tokenHash()).ifPresent(session -> {
            if (session.getRevocadaEn() == null) session.revocar(OffsetDateTime.now(ZoneOffset.UTC));
        });
    }

    @Transactional
    public StaffAccountResponse createAccount(StaffPrincipal principal, CreateStaffAccountRequest request) {
        if (request.role() == RolPersonal.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_ACCOUNT_CREATION_FORBIDDEN",
                    "La cuenta inicial de administración requiere un procedimiento fuera del API");
        }
        String email = normalizeEmail(request.email());
        if (accountRepository.findByEmailNormalizado(email).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "STAFF_ACCOUNT_EXISTS", "La cuenta ya existe");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        try {
            CuentaPersonal account = CuentaPersonal.crear(UUID.randomUUID().toString(), email,
                    request.fullName().trim(), passwordEncoder.encode(request.password()), request.role(), now);
            CuentaPersonal saved=accountRepository.saveAndFlush(account);
            audit.record(principal,"STAFF_ACCOUNT_CREATED","CUENTA_PERSONAL",saved.getId(),now);
            return StaffAccountResponse.of(saved, null);
        } catch (DataIntegrityViolationException collision) {
            throw new ApiException(HttpStatus.CONFLICT, "STAFF_ACCOUNT_EXISTS", "La cuenta ya existe");
        }
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
