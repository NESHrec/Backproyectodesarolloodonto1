package com.clinicaserena.auth.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class IdentityRateLimiterTest {
    @Test void limitaUnaIdentidadSinBloquearMasDeDiezDistintas() {
        IdentityRateLimiter limiter=new IdentityRateLimiter(2,900,100);
        for(int i=0;i<12;i++) assertThat(limiter.acquire("recover","patient-"+i+"@example.test")).isTrue();
        assertThat(limiter.acquire("recover","same@example.test")).isTrue();
        assertThat(limiter.acquire("recover","same@example.test")).isTrue();
        assertThat(limiter.acquire("recover","same@example.test")).isFalse();
    }
    @Test void rechazaClavesNuevasCuandoAlcanzaElTopeDeMemoria() {
        IdentityRateLimiter limiter=new IdentityRateLimiter(3,900,2);
        assertThat(limiter.acquire("register","one@example.test")).isTrue();
        assertThat(limiter.acquire("register","two@example.test")).isTrue();
        assertThat(limiter.acquire("register","three@example.test")).isFalse();
    }
}
