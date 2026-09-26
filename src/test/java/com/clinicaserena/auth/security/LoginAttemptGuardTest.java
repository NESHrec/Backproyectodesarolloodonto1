package com.clinicaserena.auth.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptGuardTest {

    @Test
    void eliminaEntradasExpiradasAunqueNoSeVuelvaAConsultarLaClave() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-26T15:00:00Z"));
        LoginAttemptGuard guard = new LoginAttemptGuard(3, 3, 10, 10, 20, clock);

        guard.recordFailure("account-a|address-a", "address-a");
        assertThat(guard.trackedEntriesForTests()).isEqualTo(2);

        clock.advanceSeconds(11);
        guard.purgeExpiredEntries();

        assertThat(guard.trackedEntriesForTests()).isZero();
    }

    @Test
    void limitaElNumeroTotalDeEntradasYExpulsaLaMasAntigua() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-26T15:00:00Z"));
        LoginAttemptGuard guard = new LoginAttemptGuard(3, 3, 60, 60, 4, clock);

        guard.recordFailure("account-a|address-a", "address-a");
        guard.recordFailure("account-b|address-b", "address-b");
        guard.recordFailure("account-c|address-c", "address-c");

        assertThat(guard.trackedEntriesForTests()).isLessThanOrEqualTo(4);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
