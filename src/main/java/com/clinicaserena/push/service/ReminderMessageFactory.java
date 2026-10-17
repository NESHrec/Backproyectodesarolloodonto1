package com.clinicaserena.push.service;

import com.clinicaserena.common.time.BusinessTime;
import com.clinicaserena.push.sender.PushMessage;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Texto del recordatorio: solo fecha y hora en {@link BusinessTime#ZONE}. Sin médico, especialidad,
 * notas, diagnóstico, receta ni nombre del paciente (se ve en la pantalla de bloqueo). «hoy» o
 * «mañana» salen de comparar las fechas LOCALES de la cita y del envío; si no es ninguna, se omite.
 */
@Component
public class ReminderMessageFactory {

    public static final String TITLE = "Recordatorio de cita";
    public static final String TYPE = "APPOINTMENT_REMINDER";

    private static final Locale SPANISH = Locale.forLanguageTag("es-GT");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", SPANISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", SPANISH);

    public PushMessage build(String appointmentId, OffsetDateTime scheduledAt, OffsetDateTime now) {
        ZonedDateTime local = scheduledAt.atZoneSameInstant(BusinessTime.ZONE);
        LocalDate today = now.atZoneSameInstant(BusinessTime.ZONE).toLocalDate();
        String when;
        if (local.toLocalDate().equals(today)) {
            when = "hoy, ";
        } else if (local.toLocalDate().equals(today.plusDays(1))) {
            when = "mañana, ";
        } else {
            when = "el ";
        }
        String body = "Tienes una cita " + when + DAY.format(local) + ", a las " + TIME.format(local)
                + " (hora de Guatemala). Abre la app para ver los detalles.";
        return new PushMessage(TITLE, body, Map.of("type", TYPE, "appointmentId", appointmentId));
    }
}
