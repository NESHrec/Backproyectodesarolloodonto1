package com.clinicaserena.push;

import com.clinicaserena.push.config.PushConfigurationGuard;
import com.clinicaserena.push.config.PushProvider;
import com.clinicaserena.push.config.PushSettings;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PushConfigurationGuardTest {

    private static PushSettings settings(PushProvider provider, boolean reminders) {
        return new PushSettings(provider, reminders, Duration.ofHours(2), Duration.ofMinutes(10), 3, 100);
    }

    private static MockEnvironment profiles(String... active) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(active);
        return environment;
    }

    @Test
    void elEnvioSimuladoNoArrancaFueraDeDevOTest() {
        assertThatThrownBy(() -> new PushConfigurationGuard(settings(PushProvider.SIMULATED, false), profiles()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("solo se permite con el perfil dev o test");
        assertThatThrownBy(() -> new PushConfigurationGuard(settings(PushProvider.SIMULATED, true), profiles("prod")))
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> new PushConfigurationGuard(settings(PushProvider.SIMULATED, true), profiles("dev"))).doesNotThrowAnyException();
        assertThatCode(() -> new PushConfigurationGuard(settings(PushProvider.SIMULATED, false), profiles("test"))).doesNotThrowAnyException();
    }

    @Test
    void laTareaEncendidaExigeProveedor() {
        assertThatThrownBy(() -> new PushConfigurationGuard(settings(PushProvider.NONE, true), profiles("dev")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("requiere clinica.push.provider");
        assertThatCode(() -> new PushConfigurationGuard(settings(PushProvider.NONE, false), profiles())).doesNotThrowAnyException();
    }

    @Test
    void validaUmbralesDeLaConfiguracion() {
        assertThatThrownBy(() -> new PushSettings(PushProvider.NONE, false, Duration.ofHours(24), Duration.ofMinutes(10), 3, 100))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PushSettings(PushProvider.NONE, false, Duration.ofHours(2), Duration.ZERO, 3, 100))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PushSettings(PushProvider.NONE, false, Duration.ofHours(2), Duration.ofMinutes(10), 0, 100))
                .isInstanceOf(IllegalStateException.class);
    }
}
