package com.clinicaserena.auth.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
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

    private static final String KEY_PREFIX = "clinica:auth:login-limit:";
    private static final DefaultRedisScript<Long> RECORD_FAILURE_SCRIPT = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]) "
                    + "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "if count >= tonumber(ARGV[2]) then redis.call('SET', KEYS[2], '1', 'EX', ARGV[3]) end "
                    + "return count",
            Long.class);

    private final Map<String, AttemptState> accountAttempts = new HashMap<>();
    private final Map<String, AttemptState> addressAttempts = new HashMap<>();
    private final int maxFailuresPerAccount;
    private final int maxFailuresPerAddress;
    private final Duration window;
    private final Duration cooldown;
    private final int maxEntries;
    private final Clock clock;
    private final StringRedisTemplate redis;
    private final boolean redisEnabled;

    @Autowired
    public LoginAttemptGuard(
            @Value("${clinica.auth.login-rate-limit.max-failures:5}") int maxFailuresPerAccount,
            @Value("${clinica.auth.login-rate-limit.max-failures-per-address:20}") int maxFailuresPerAddress,
            @Value("${clinica.auth.login-rate-limit.window-seconds:60}") long windowSeconds,
            @Value("${clinica.auth.login-rate-limit.cooldown-seconds:60}") long cooldownSeconds,
            @Value("${clinica.auth.login-rate-limit.max-entries:10000}") int maxEntries,
            ObjectProvider<StringRedisTemplate> redisProvider,
            @Value("${clinica.auth.login-rate-limit.backend:local}") String backend
    ) {
        this(maxFailuresPerAccount, maxFailuresPerAddress, windowSeconds, cooldownSeconds, maxEntries,
                Clock.systemUTC(), redisProvider.getIfAvailable(), backend);
    }

    LoginAttemptGuard(
            int maxFailuresPerAccount,
            int maxFailuresPerAddress,
            long windowSeconds,
            long cooldownSeconds,
            int maxEntries,
            Clock clock
    ) {
        this(maxFailuresPerAccount, maxFailuresPerAddress, windowSeconds, cooldownSeconds, maxEntries,
                clock, null, "local");
    }

    LoginAttemptGuard(
            int maxFailuresPerAccount,
            int maxFailuresPerAddress,
            long windowSeconds,
            long cooldownSeconds,
            int maxEntries,
            Clock clock,
            StringRedisTemplate redis,
            String backend
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
        this.redisEnabled = "redis".equalsIgnoreCase(backend);
        if (redisEnabled && redis == null) {
            throw new IllegalStateException("El rate limit Redis está habilitado pero Redis no está configurado");
        }
        this.redis = redis;
    }

    public synchronized boolean isAllowed(String accountKey, String remoteAddress) {
        if (redisEnabled) {
            return !redis.hasKey(blockKey("account", accountKey))
                    && !redis.hasKey(blockKey("address", remoteAddress));
        }
        Instant now = clock.instant();
        purgeExpiredEntries(now);
        return isAllowed(accountAttempts.get(accountKey), now)
                && isAllowed(addressAttempts.get(remoteAddress), now);
    }

    public synchronized void recordFailure(String accountKey, String remoteAddress) {
        if (redisEnabled) {
            recordRedisFailure("account", accountKey, maxFailuresPerAccount);
            recordRedisFailure("address", remoteAddress, maxFailuresPerAddress);
            return;
        }
        Instant now = clock.instant();
        purgeExpiredEntries(now);
        recordFailure(accountAttempts, accountKey, maxFailuresPerAccount, now);
        recordFailure(addressAttempts, remoteAddress, maxFailuresPerAddress, now);
    }

    public synchronized void recordSuccess(String accountKey, String remoteAddress) {
        if (redisEnabled) {
            redis.delete(java.util.List.of(
                    counterKey("account", accountKey), blockKey("account", accountKey),
                    counterKey("address", remoteAddress), blockKey("address", remoteAddress)));
            return;
        }
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

    private void recordRedisFailure(String kind, String value, int limit) {
        String counter = counterKey(kind, value);
        redis.execute(RECORD_FAILURE_SCRIPT,
                java.util.List.of(counter, blockKey(kind, value)),
                Long.toString(window.toSeconds()), Integer.toString(limit), Long.toString(cooldown.toSeconds()));
    }

    private static String counterKey(String kind, String value) {
        return KEY_PREFIX + kind + ":" + TokenHasher.sha256(value == null ? "<null>" : value);
    }

    private static String blockKey(String kind, String value) {
        return counterKey(kind, value) + ":blocked";
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
