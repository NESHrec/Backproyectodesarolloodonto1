package com.clinicaserena.push.config;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Impide arrancar con una configuración push peligrosa: el envío simulado fuera de dev o test, o la
 * tarea encendida sin proveedor. Falla al iniciar, con un mensaje claro, en lugar de quedar a medias.
 */
@Component
public class PushConfigurationGuard {

    public PushConfigurationGuard(PushSettings settings, Environment environment) {
        if (settings.provider() == PushProvider.SIMULATED && !environment.acceptsProfiles(Profiles.of("dev", "test"))) {
            throw new IllegalStateException(
                    "clinica.push.provider=SIMULATED solo se permite con el perfil dev o test; no debe existir en producción");
        }
        if (settings.remindersEnabled() && !settings.enabled()) {
            throw new IllegalStateException(
                    "clinica.push.reminders.enabled=true requiere clinica.push.provider distinto de NONE");
        }
    }
}
