package com.clinicaserena.push.sender;

import com.clinicaserena.push.config.PushSettings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Envío SIMULADO para desarrollo y pruebas: no hace llamadas de red, no entrega nada y solo acepta
 * tokens de prueba ({@code SIMULADO-…}). Existe únicamente con {@code clinica.push.provider=SIMULATED}
 * y el perfil dev o test. Guarda en memoria qué se habría enviado (sin el token) para las pruebas.
 */
@Component
@Profile({"dev", "test"})
@ConditionalOnProperty(name = "clinica.push.provider", havingValue = "SIMULATED")
public class SimulatedPushSender implements PushSender {

    /** Registro de un envío simulado: dispositivo y mensaje, nunca el token. */
    public record SimulatedDelivery(String deviceId, PushMessage message) {}

    private final List<SimulatedDelivery> deliveries = new CopyOnWriteArrayList<>();

    @Override
    public PushDeliveryResult send(PushTarget target, PushMessage message) {
        if (target.token() == null || !target.token().startsWith(PushSettings.SIMULATED_TOKEN_PREFIX)) {
            return PushDeliveryResult.failed("TOKEN_NO_SIMULADO");
        }
        deliveries.add(new SimulatedDelivery(target.deviceId(), message));
        return PushDeliveryResult.simulated();
    }

    public List<SimulatedDelivery> deliveries() {
        return List.copyOf(deliveries);
    }

    public void clear() {
        deliveries.clear();
    }
}
