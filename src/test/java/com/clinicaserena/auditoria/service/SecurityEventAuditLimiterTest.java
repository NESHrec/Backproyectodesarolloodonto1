package com.clinicaserena.auditoria.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityEventAuditLimiterTest {

    @Test
    void deduplicaLaMismaRutaYAcotaEventosPorVentana() {
        AtomicLong ticker = new AtomicLong(0);
        SecurityEventAuditLimiter limiter = new SecurityEventAuditLimiter(2, Duration.ofMinutes(1), ticker::get);

        assertThat(limiter.shouldPersist("HTTP_401", request("203.0.113.1", "GET", "/private/a"))).isTrue();
        assertThat(limiter.shouldPersist("HTTP_401", request("203.0.113.1", "GET", "/private/a"))).isFalse();
        assertThat(limiter.shouldPersist("HTTP_403", request("203.0.113.2", "POST", "/private/b"))).isTrue();
        assertThat(limiter.shouldPersist("HTTP_401", request("203.0.113.3", "GET", "/private/c"))).isFalse();

        ticker.addAndGet(Duration.ofMinutes(1).toNanos());
        assertThat(limiter.shouldPersist("HTTP_401", request("203.0.113.3", "GET", "/private/c"))).isTrue();
    }

    private static MockHttpServletRequest request(String address, String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(address);
        return request;
    }
}
