package com.clinicaserena.push;

import com.clinicaserena.push.sender.PushMessage;
import com.clinicaserena.push.service.ReminderMessageFactory;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** «hoy» y «mañana» salen de las fechas en America/Guatemala (UTC−6), no de las fechas UTC. */
class ReminderMessageFactoryTest {

    private final ReminderMessageFactory factory = new ReminderMessageFactory();

    private static OffsetDateTime gt(String localDateTime) {
        return OffsetDateTime.parse(localDateTime + "-06:00");
    }

    @Test
    void mananaConLaHoraLocalDeGuatemala() {
        // Cita el sábado 17 a las 08:00 en Guatemala (14:00 Z); envío el viernes 16 a las 10:00 local.
        PushMessage message = factory.build("cita-1", OffsetDateTime.parse("2026-10-17T14:00:00Z"), gt("2026-10-16T10:00:00"));

        assertThat(message.title()).isEqualTo("Recordatorio de cita");
        assertThat(message.body()).isEqualTo(
                "Tienes una cita mañana, sábado 17 de octubre, a las 08:00 (hora de Guatemala). Abre la app para ver los detalles.");
    }

    @Test
    void hoyCuandoLaCitaEsElMismoDiaLocalAunqueEnUtcSeaOtroDia() {
        // Ventana de 22 a 24 h: envío a la 01:00 y cita a las 23:30 del MISMO día local (en UTC ya es el 18).
        PushMessage message = factory.build("cita-1", gt("2026-10-17T23:30:00"), gt("2026-10-17T01:00:00"));

        assertThat(message.body()).isEqualTo(
                "Tienes una cita hoy, sábado 17 de octubre, a las 23:30 (hora de Guatemala). Abre la app para ver los detalles.");
    }

    @Test
    void cercaDeMedianocheSeUsaLaFechaLocalYNoLaUtc() {
        // Envío el 16 a las 18:30 local (ya es 17 en UTC); cita el 17 a las 17:00 local (también 17 en UTC).
        // En UTC serían el mismo día («hoy»); en Guatemala es «mañana».
        PushMessage tomorrow = factory.build("cita-1", gt("2026-10-17T17:00:00"), gt("2026-10-16T18:30:00"));
        assertThat(tomorrow.body()).startsWith("Tienes una cita mañana, sábado 17 de octubre, a las 17:00");

        // Envío a las 23:59 del 16; cita a las 22:30 del 17 local.
        PushMessage lateNight = factory.build("cita-1", gt("2026-10-17T22:30:00"), gt("2026-10-16T23:59:00"));
        assertThat(lateNight.body()).startsWith("Tienes una cita mañana, sábado 17 de octubre, a las 22:30");
    }

    @Test
    void otraFechaSoloDiaFechaYHora() {
        PushMessage message = factory.build("cita-1", gt("2026-10-19T09:15:00"), gt("2026-10-16T09:00:00"));

        assertThat(message.body()).isEqualTo(
                "Tienes una cita el lunes 19 de octubre, a las 09:15 (hora de Guatemala). Abre la app para ver los detalles.");
    }

    @Test
    void sinDatosClinicosNiPersonales() {
        PushMessage message = factory.build("cita-1", OffsetDateTime.parse("2026-10-17T14:00:00Z"), gt("2026-10-16T10:00:00"));

        assertThat(message.data()).isEqualTo(Map.of("type", "APPOINTMENT_REMINDER", "appointmentId", "cita-1"));
        String visible = (message.title() + " " + message.body()).toLowerCase();
        for (String forbidden : new String[] {"dr", "doctor", "médico", "especialidad", "diagn", "receta", "nota", "odonto"}) {
            assertThat(visible).doesNotContain(forbidden);
        }
    }
}
