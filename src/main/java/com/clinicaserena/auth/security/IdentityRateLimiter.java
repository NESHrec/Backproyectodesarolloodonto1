package com.clinicaserena.auth.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.concurrent.ConcurrentHashMap;

/** Per-identity limiter: deliberately independent of proxy/client IP. */
@Component
public class IdentityRateLimiter {
    private record Window(Instant started, int count) {}
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int maximum;
    private final Duration duration;
    private final int maxEntries;
    public IdentityRateLimiter(@Value("${clinica.auth.public-rate-limit.max-requests:3}") int maximum,
            @Value("${clinica.auth.public-rate-limit.window-seconds:900}") long seconds,
            @Value("${clinica.auth.public-rate-limit.max-entries:10000}") int maxEntries) {
        this.maximum=maximum; this.duration=Duration.ofSeconds(seconds); this.maxEntries=maxEntries;
    }
    public boolean acquire(String operation, String normalizedIdentity) {
        Instant now=Instant.now(); String key=operation+":"+TokenHasher.sha256(normalizedIdentity);
        windows.entrySet().removeIf(entry -> entry.getValue().started.plus(duration).isBefore(now));
        if (!windows.containsKey(key) && windows.size() >= maxEntries) return false;
        Window result=windows.compute(key, (ignored, old) -> old==null || old.started.plus(duration).isBefore(now)
                ? new Window(now,1) : new Window(old.started,old.count+1));
        return result.count<=maximum;
    }
}
