package com.clinicaserena.push.service;

import com.clinicaserena.push.config.PushSettings;
import com.clinicaserena.push.repository.PushDeviceRepository;
import com.clinicaserena.push.repository.ReminderRepository;
import com.clinicaserena.push.repository.ReminderRepository.Claimed;
import com.clinicaserena.push.repository.ReminderRepository.Context;
import com.clinicaserena.push.sender.PushDeliveryResult;
import com.clinicaserena.push.sender.PushMessage;
import com.clinicaserena.push.sender.PushSender;
import com.clinicaserena.push.sender.PushTarget;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Un recordatorio por cita, 24 h antes, solo al paciente y sin datos clínicos.
 *
 * <p>Flujo sin transacciones abiertas durante el envío (esta clase no es transaccional):
 * <ol>
 *   <li>reclamar: INSERT … ON CONFLICT DO NOTHING de la fila EN_PROCESO (se confirma sola);</li>
 *   <li>leer la cita, la cuenta y los dispositivos (lecturas breves, sin bloqueos);</li>
 *   <li>enviar fuera de toda transacción;</li>
 *   <li>cerrar el estado con un UPDATE nuevo.</li>
 * </ol>
 * Un EN_PROCESO más reciente que {@code stale-after} nunca se retoma: otra instancia puede estar enviando.
 */
@Service
public class AppointmentReminderService {

    private static final Duration LEAD = Duration.ofHours(24);
    private static final Set<String> ACTIVE_APPOINTMENT = Set.of("PENDIENTE", "CONFIRMADA");

    /** Resumen de una pasada (para logs y pruebas): solo conteos. */
    public record RunSummary(int claimed, int retaken, int simulated, int delivered, int withoutDevice,
                             int skipped, int failed, int expired) {}

    private final ReminderRepository reminders;
    private final PushDeviceRepository devices;
    private final ReminderMessageFactory messages;
    private final PushSender sender;
    private final PushSettings settings;
    private final Clock clock;

    @Autowired
    public AppointmentReminderService(ReminderRepository reminders, PushDeviceRepository devices,
                                      ReminderMessageFactory messages, ObjectProvider<PushSender> sender,
                                      PushSettings settings, Clock clock) {
        this(reminders, devices, messages, sender.getIfAvailable(), settings, clock);
    }

    /** Constructor directo (pruebas con dos "instancias" y emisores controlados). */
    public AppointmentReminderService(ReminderRepository reminders, PushDeviceRepository devices,
                                      ReminderMessageFactory messages, PushSender sender,
                                      PushSettings settings, Clock clock) {
        this.reminders = reminders;
        this.devices = devices;
        this.messages = messages;
        this.sender = sender;
        this.settings = settings;
        this.clock = clock;
    }

    public RunSummary runOnce() {
        Tally tally = new Tally();
        if (sender == null) return tally.summary();
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime staleBefore = now.minus(settings.staleAfter());

        tally.expired = reminders.expireExhausted(staleBefore, settings.maxAttempts(), now);
        for (String id : reminders.retryable(staleBefore, settings.maxAttempts(), settings.batchSize())) {
            reminders.retake(id, staleBefore, settings.maxAttempts(), now).ifPresent(claimed -> {
                tally.retaken++;
                process(claimed, tally);
            });
        }

        OffsetDateTime to = now.plus(LEAD);
        OffsetDateTime from = to.minus(settings.grace());
        for (ReminderRepository.Candidate candidate : reminders.candidates(from, to, settings.batchSize())) {
            String reminderId = UUID.randomUUID().toString();
            if (reminders.claim(reminderId, candidate.appointmentId(), candidate.scheduledAt(), now)) {
                tally.claimed++;
                process(new Claimed(reminderId, candidate.appointmentId(), 1), tally);
            }
        }
        return tally.summary();
    }

    private void process(Claimed claimed, Tally tally) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Context context = reminders.context(claimed.appointmentId()).orElse(null);
        if (context == null || !ACTIVE_APPOINTMENT.contains(context.appointmentStatus())
                || !context.scheduledAt().isAfter(now)) {
            close(claimed, "OMITIDO", 0, 0, "CITA_NO_APLICA", tally);
            return;
        }
        if (context.accountId() == null || !"ACTIVA".equals(context.accountStatus()) || !context.emailVerified()) {
            close(claimed, "OMITIDO", 0, 0, "CUENTA_NO_APLICA", tally);
            return;
        }
        List<PushTarget> targets = devices.targetsForAccount(context.accountId());
        if (targets.isEmpty()) {
            close(claimed, "SIN_DISPOSITIVO", 0, 0, null, tally);
            return;
        }

        PushMessage message = messages.build(claimed.appointmentId(), context.scheduledAt(), now);
        int failed = 0;
        boolean anyDelivered = false;
        boolean anySimulated = false;
        String firstError = null;
        // Envío fuera de transacción: no hay conexiones ni bloqueos de la BD retenidos aquí.
        for (PushTarget target : targets) {
            PushDeliveryResult result = safeSend(target, message);
            switch (result.outcome()) {
                case DELIVERED -> anyDelivered = true;
                case SIMULATED -> anySimulated = true;
                case INVALID_TOKEN, FAILED -> {
                    failed++;
                    if (firstError == null) firstError = result.errorCode();
                    if (result.outcome() == PushDeliveryResult.Outcome.INVALID_TOKEN) devices.deleteById(target.deviceId());
                }
            }
        }
        // Al menos un dispositivo bien → SIMULADO (o ENVIADO con un proveedor real); FALLIDO solo si fallaron todos.
        String status = anyDelivered ? "ENVIADO" : anySimulated ? "SIMULADO" : "FALLIDO";
        close(claimed, status, targets.size(), failed, failed > 0 ? firstError : null, tally);
    }

    /** Las excepciones del proveedor nunca se propagan con su mensaje (podría incluir el token). */
    private PushDeliveryResult safeSend(PushTarget target, PushMessage message) {
        try {
            PushDeliveryResult result = sender.send(target, message);
            return result != null ? result : PushDeliveryResult.failed("SIN_RESULTADO");
        } catch (RuntimeException ex) {
            return PushDeliveryResult.failed("ERROR_PROVEEDOR");
        }
    }

    private void close(Claimed claimed, String status, int attempted, int failed, String error, Tally tally) {
        reminders.finish(claimed.reminderId(), status, attempted, failed, error, OffsetDateTime.now(clock));
        switch (status) {
            case "SIMULADO" -> tally.simulated++;
            case "ENVIADO" -> tally.delivered++;
            case "SIN_DISPOSITIVO" -> tally.withoutDevice++;
            case "OMITIDO" -> tally.skipped++;
            default -> tally.failed++;
        }
    }

    private static final class Tally {
        int claimed, retaken, simulated, delivered, withoutDevice, skipped, failed, expired;

        RunSummary summary() {
            return new RunSummary(claimed, retaken, simulated, delivered, withoutDevice, skipped, failed, expired);
        }
    }
}
