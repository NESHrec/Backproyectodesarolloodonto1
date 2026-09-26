package com.clinicaserena.auth.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Limitador local y temporal de intentos. Aplica un límite por correo y
 * dirección remota y otro por dirección remota para evitar eludirlo cambiando
 * de correo. Ninguno modifica el estado persistido de una cuenta.
 */
@Service
public class LoginAttemptGuard {

    private final Map<String, AttemptState> accountAttempts = new HashMap<>();
    private final Map<String, AttemptState> addressAttempts = new HashMap<>();
    private final int maxFailuresPerAccount;
    private final int maxFailuresPerAddress;
    private final Duration window;
    private final Duration cooldown;
    private final int maxEntries;
    private final Clock clock;

    @Autowired
    public LoginAttemptGuard(
            @Value("${clinica.auth.login-rate-limit.max-failures:5}") int maxFailuresPerAccount,
            @Value("${clinica.auth.login-rate-limit.max-failures-per-address:20}") int maxFailuresPerAddress,
            @Value("${clinica.auth.login-rate-limit.window-seconds:60}") long windowSeconds,
            @Value("${clinica.auth.login-rate-limit.cooldown-seconds:60}") long cooldownSeconds,
            @Value("${clinica.auth.login-rate-limit.max-entries:10000}") int maxEntries
    ) {
        this(maxFailuresPerAccount, maxFailuresPerAddress, windowSeconds, cooldownSeconds, maxEntries, Clock.systemUTC());
    }

    LoginAttemptGuard(
            int maxFailuresPerAccount,
            int maxFailuresPerAddress,
            long windowSeconds,
            long cooldownSeconds,
            int maxEntries,
            Clock clock
    ) {
        if (maxFailuresPerAccount < 1 || maxFailuresPerAddress < 1
                || windowSeconds < 1 || cooldownSeconds < 1 || maxEntries < 2) {
            throw new IllegalArgumentException("La configuración del límite de login no es válida");
        }
        this.maxFailuresPerAccount = maxFailuresPerAccount;
        this.maxFailuresPerAddress = maxFailuresPerAddress;
        this.window = Duration.ofSeconds(windowSeconds);
        this.cooldown = Duration.ofSeconds(cooldownSeconds);
        this.maxEntries = maxEntries;
        this.clock = clock;
    }

    public synchronized boolean isAllowed(String accountKey, String remoteAddress) {
        Instant now = clock.instant();
        purgeExpiredEntries(now);
        return isAllowed(accountAttempts.get(accountKey), now)
                && isAllowed(addressAttempts.get(remoteAddress), now);
    }

    public synchronized void recordFailure(String accountKey, String remoteAddress) {
        Instant now = clock.instant();
        purgeExpiredEntries(now);
        recordFailure(accountAttempts, accountKey, maxFailuresPerAccount, now);
        recordFailure(addressAttempts, remoteAddress, maxFailuresPerAddress, now);
    }

    public synchronized void recordSuccess(String accountKey, String remoteAddress) {
        accountAttempts.remove(accountKey);
        addressAttempts.remove(remoteAddress);
    }

    /**
     * Limpia periódicamente claves que no vuelven a recibir tráfico. El límite
     * de entradas se aplica al total de ambas tablas y expulsa primero la clave
     * menos recientemente utilizada cuando se alcanza la capacidad.
     */
    @Scheduled(fixedDelayString = "${clinica.auth.login-rate-limit.cleanup-interval-milliseconds:60000}")
    public synchronized void purgeExpiredEntries() {
        purgeExpiredEntries(clock.instant());
    }

    int trackedEntriesForTests() {
        synchronized (this) {
            return accountAttempts.size() + addressAttempts.size();
        }
    }

    private void recordFailure(
            Map<String, AttemptState> states,
            String key,
            int failureLimit,
            Instant now
    ) {
        AttemptState state = states.get(key);
        if (state == null) {
            ensureCapacity(now);
            state = new AttemptState(now);
            states.put(key, state);
        }
        state.lastTouched = now;
        state.failures++;
        if (state.failures >= failureLimit) {
            state.blockedUntil = now.plus(cooldown);
        }
    }

    private boolean isAllowed(AttemptState state, Instant now) {
        if (state == null) {
            return true;
        }
        state.lastTouched = now;
        return state.blockedUntil == null || !now.isBefore(state.blockedUntil);
    }

    private void ensureCapacity(Instant now) {
        purgeExpiredEntries(now);
        while (accountAttempts.size() + addressAttempts.size() >= maxEntries) {
            removeLeastRecentlyTouched();
        }
    }

    private void removeLeastRecentlyTouched() {
        Map<String, AttemptState> oldestMap = null;
        String oldestKey = null;
        Instant oldest = null;
        for (Map<String, AttemptState> states : new Map[]{accountAttempts, addressAttempts}) {
            for (Map.Entry<String, AttemptState> entry : states.entrySet()) {
                if (oldest == null || entry.getValue().lastTouched.isBefore(oldest)) {
                    oldestMap = states;
                    oldestKey = entry.getKey();
                    oldest = entry.getValue().lastTouched;
                }
            }
        }
        if (oldestMap != null) {
            oldestMap.remove(oldestKey);
        }
    }

    private void purgeExpiredEntries(Instant now) {
        removeExpired(accountAttempts, now);
        removeExpired(addressAttempts, now);
    }

    private void removeExpired(Map<String, AttemptState> states, Instant now) {
        Iterator<Map.Entry<String, AttemptState>> iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            AttemptState state = iterator.next().getValue();
            Instant expiresAt = state.windowStarted.plus(window);
            if (state.blockedUntil != null && state.blockedUntil.isAfter(expiresAt)) {
                expiresAt = state.blockedUntil;
            }
            if (!now.isBefore(expiresAt)) {
                iterator.remove();
            }
        }
    }

    private static final class AttemptState {
        private final Instant windowStarted;
        private Instant lastTouched;
        private int failures;
        private Instant blockedUntil;

        private AttemptState(Instant startedAt) {
            this.windowStarted = startedAt;
            this.lastTouched = startedAt;
        }
    }
}
