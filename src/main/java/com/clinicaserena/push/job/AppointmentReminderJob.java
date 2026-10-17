package com.clinicaserena.push.job;

import com.clinicaserena.push.service.AppointmentReminderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarea programada de recordatorios. Apagada por defecto en todos los perfiles: se enciende solo con
 * {@code clinica.push.reminders.enabled=true} (variable CLINICA_PUSH_REMINDERS_ENABLED). Registra solo
 * conteos, nunca tokens ni contenido.
 */
@Component
@ConditionalOnProperty(name = "clinica.push.reminders.enabled", havingValue = "true")
public class AppointmentReminderJob {

    private static final Logger log = LoggerFactory.getLogger(AppointmentReminderJob.class);

    private final AppointmentReminderService service;

    public AppointmentReminderJob(AppointmentReminderService service) {
        this.service = service;
    }

    @Scheduled(initialDelayString = "${clinica.push.reminders.initial-delay-ms:30000}",
            fixedDelayString = "${clinica.push.reminders.interval-ms:300000}")
    public void run() {
        AppointmentReminderService.RunSummary s = service.runOnce();
        if (s.claimed() + s.retaken() + s.expired() > 0) {
            log.info("Recordatorios de cita: reclamados={} retomados={} simulados={} enviados={} sinDispositivo={} omitidos={} fallidos={} vencidos={}",
                    s.claimed(), s.retaken(), s.simulated(), s.delivered(), s.withoutDevice(), s.skipped(), s.failed(), s.expired());
        }
    }
}
