package com.clinicaserena.auditoria.service;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Bounds persistence of generic HTTP security events. Authentication failures
 * are separately rate-limited and audited by the authentication services.
 */
public final class SecurityEventAuditLimiter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityEventAuditLimiter.class);
    private static final int DEFAULT_MAX_EVENTS_PER_WINDOW = 30;
    private static final Duration DEFAULT_WINDOW = Duration.ofMinutes(1);

    private final int maxEventsPerWindow;
    private final long windowNanos;
    private final LongSupplier ticker;
    private final Set<String> seenKeys = new HashSet<>();
    private long windowStartedAt;
    private int persistedInWindow;
    private long suppressedInWindow;

    public SecurityEventAuditLimiter() {
        this(DEFAULT_MAX_EVENTS_PER_WINDOW, DEFAULT_WINDOW, System::nanoTime);
    }

    SecurityEventAuditLimiter(int maxEventsPerWindow, Duration window, LongSupplier ticker) {
        if (maxEventsPerWindow < 1 || window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("La configuración del límite de auditoría no es válida");
        }
        this.maxEventsPerWindow = maxEventsPerWindow;
        this.windowNanos = window.toNanos();
        this.ticker = ticker;
        this.windowStartedAt = ticker.getAsLong();
    }

    public synchronized boolean shouldPersist(String action, HttpServletRequest request) {
        long now = ticker.getAsLong();
        if (now - windowStartedAt >= windowNanos) {
            if (suppressedInWindow > 0) {
                LOGGER.warn("Se omitieron {} eventos HTTP de auditoría por el límite de persistencia",
                        suppressedInWindow);
            }
            windowStartedAt = now;
            persistedInWindow = 0;
            suppressedInWindow = 0;
            seenKeys.clear();
        }

        String source = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        String key = action + "|" + source + "|" + request.getMethod() + "|" + request.getRequestURI();
        if (persistedInWindow >= maxEventsPerWindow || !seenKeys.add(key)) {
            suppressedInWindow++;
            return false;
        }
        persistedInWindow++;
        return true;
    }
}
