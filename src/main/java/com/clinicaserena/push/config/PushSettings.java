package com.clinicaserena.push.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuración de recordatorios push ({@code clinica.push.*}). Sin Firebase, el único proveedor real
 * es el simulado y solo existe en dev y test; la tarea programada está apagada por defecto.
 */
@Component
public class PushSettings {

    public static final String SIMULATED_TOKEN_PREFIX = "SIMULADO-";

    private final PushProvider provider;
    private final boolean remindersEnabled;
    private final Duration grace;
    private final Duration staleAfter;
    private final int maxAttempts;
    private final int batchSize;

    public PushSettings(
            @Value("${clinica.push.provider:NONE}") PushProvider provider,
            @Value("${clinica.push.reminders.enabled:false}") boolean remindersEnabled,
            @Value("${clinica.push.reminders.grace:PT2H}") Duration grace,
            @Value("${clinica.push.reminders.stale-after:PT10M}") Duration staleAfter,
            @Value("${clinica.push.reminders.max-attempts:3}") int maxAttempts,
            @Value("${clinica.push.reminders.batch-size:100}") int batchSize) {
        if (grace.isNegative() || grace.isZero() || grace.compareTo(Duration.ofHours(24)) >= 0) {
            throw new IllegalStateException("clinica.push.reminders.grace debe estar entre 0 y 24 horas");
        }
        if (staleAfter.isNegative() || staleAfter.isZero()) {
            throw new IllegalStateException("clinica.push.reminders.stale-after debe ser positivo");
        }
        if (maxAttempts < 1 || maxAttempts > 10 || batchSize < 1) {
            throw new IllegalStateException("clinica.push.reminders.max-attempts (1-10) o batch-size no válidos");
        }
        this.provider = provider;
        this.remindersEnabled = remindersEnabled;
        this.grace = grace;
        this.staleAfter = staleAfter;
        this.maxAttempts = maxAttempts;
        this.batchSize = batchSize;
    }

    public PushProvider provider() { return provider; }
    public boolean enabled() { return provider != PushProvider.NONE; }
    public boolean remindersEnabled() { return remindersEnabled; }
    public Duration grace() { return grace; }
    public Duration staleAfter() { return staleAfter; }
    public int maxAttempts() { return maxAttempts; }
    public int batchSize() { return batchSize; }
}
