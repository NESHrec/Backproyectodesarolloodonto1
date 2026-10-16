package com.clinicaserena.push.service;

import com.clinicaserena.auditoria.service.BitacoraService;
import com.clinicaserena.auth.security.PatientPrincipal;
import com.clinicaserena.auth.security.TokenHasher;
import com.clinicaserena.common.exception.ApiException;
import com.clinicaserena.push.config.PushProvider;
import com.clinicaserena.push.config.PushSettings;
import com.clinicaserena.push.dto.RegisterPushDeviceRequest;
import com.clinicaserena.push.dto.RemovePushDeviceRequest;
import com.clinicaserena.push.repository.PushDeviceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Registro y retiro del token push del paciente autenticado. La cuenta sale del Bearer, nunca del
 * cuerpo. El token no se devuelve, no se audita y no se registra en logs.
 */
@Service
public class PushDeviceService {

    private final PushDeviceRepository devices;
    private final PushSettings settings;
    private final BitacoraService audit;
    private final Clock clock;

    public PushDeviceService(PushDeviceRepository devices, PushSettings settings, BitacoraService audit, Clock clock) {
        this.devices = devices;
        this.settings = settings;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public void register(PatientPrincipal principal, RegisterPushDeviceRequest request) {
        if (!settings.enabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "PUSH_NOT_ENABLED", "Los recordatorios push no están disponibles");
        }
        String token = request.token().trim();
        if (settings.provider() == PushProvider.SIMULATED && !token.startsWith(PushSettings.SIMULATED_TOKEN_PREFIX)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PUSH_TOKEN_NOT_SIMULATED",
                    "En este entorno solo se aceptan tokens de prueba simulados");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        String deviceId = devices.upsert(UUID.randomUUID().toString(), principal.accountId(), principal.tokenHash(),
                TokenHasher.sha256(token), token, request.platform().name(), now);
        audit.recordPatient(principal, "PUSH_DEVICE_REGISTERED", "DISPOSITIVO_PUSH", deviceId, now);
    }

    /** Idempotente: si el token no existe o es de otra cuenta, no hace nada y no lo revela. */
    @Transactional
    public void remove(PatientPrincipal principal, RemovePushDeviceRequest request) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (String deviceId : devices.deleteOwn(principal.accountId(), TokenHasher.sha256(request.token().trim()))) {
            audit.recordPatient(principal, "PUSH_DEVICE_REMOVED", "DISPOSITIVO_PUSH", deviceId, now);
        }
    }
}
