package com.clinicaserena.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class ClockConfigTest {

    @Test
    void configuredClockUsesUtcZone() {
        assertEquals(ZoneOffset.UTC, new ClockConfig().clock().getZone());
    }
}
