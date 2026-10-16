package com.clinicaserena.push.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Recordatorios de citas. Se usa SIN transacciones envolventes: cada sentencia se confirma sola
 * (autocommit), así reclamar, retomar y cerrar un recordatorio nunca dejan bloqueos abiertos mientras
 * se envía la notificación.
 */
@Repository
public class ReminderRepository {

    public static final String TYPE_24H = "CITA_24H";

    public record Candidate(String appointmentId, OffsetDateTime scheduledAt) {}

    public record Claimed(String reminderId, String appointmentId, int attempts) {}

    /** Datos mínimos para decidir el envío; sin notas, médico ni especialidad. */
    public record Context(String appointmentStatus, OffsetDateTime scheduledAt, String accountId,
                          String accountStatus, boolean emailVerified) {}

    private final JdbcTemplate jdbc;

    public ReminderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Citas activas cuya hora cae en (desde, hasta] y que aún no tienen recordatorio. */
    public List<Candidate> candidates(OffsetDateTime from, OffsetDateTime to, int limit) {
        return jdbc.query("""
                SELECT c.id, c.programada_en FROM citas c
                WHERE c.estado IN ('PENDIENTE', 'CONFIRMADA')
                  AND c.programada_en > ? AND c.programada_en <= ?
                  AND NOT EXISTS (SELECT 1 FROM recordatorios_cita r WHERE r.cita_id = c.id AND r.tipo = ?)
                ORDER BY c.programada_en
                LIMIT ?
                """, (rs, n) -> new Candidate(rs.getString(1), rs.getObject(2, OffsetDateTime.class)),
                from, to, TYPE_24H, limit);
    }

    /**
     * Reclama la cita: inserta la fila EN_PROCESO si no existe. Solo quien la inserta envía; la
     * restricción única uq_recordatorio_cita_tipo impide duplicados entre pasadas e instancias.
     */
    public boolean claim(String reminderId, String appointmentId, OffsetDateTime scheduledAt, OffsetDateTime now) {
        return jdbc.update("""
                INSERT INTO recordatorios_cita (id, cita_id, tipo, estado, programada_en, intentos, creado_en, actualizado_en)
                VALUES (?, ?, ?, 'EN_PROCESO', ?, 1, ?, ?)
                ON CONFLICT (cita_id, tipo) DO NOTHING
                """, reminderId, appointmentId, TYPE_24H, scheduledAt, now, now) == 1;
    }

    /**
     * Recordatorios que se pueden retomar: FALLIDO, o EN_PROCESO ABANDONADO (sin actualizar desde antes
     * del umbral). Un EN_PROCESO más reciente que el umbral nunca aparece aquí.
     */
    public List<String> retryable(OffsetDateTime staleBefore, int maxAttempts, int limit) {
        return jdbc.queryForList("""
                SELECT id FROM recordatorios_cita
                WHERE estado IN ('EN_PROCESO', 'FALLIDO') AND actualizado_en < ? AND intentos < ?
                ORDER BY actualizado_en
                LIMIT ?
                """, String.class, staleBefore, maxAttempts, limit);
    }

    /**
     * Retoma un recordatorio con una sola sentencia condicional: solo una instancia puede afectar la
     * fila, y vuelve a EN_PROCESO con el reloj renovado (la otra instancia ya no la ve como abandonada).
     */
    public Optional<Claimed> retake(String reminderId, OffsetDateTime staleBefore, int maxAttempts, OffsetDateTime now) {
        return jdbc.query("""
                UPDATE recordatorios_cita
                SET estado = 'EN_PROCESO', intentos = intentos + 1, actualizado_en = ?, error_codigo = NULL
                WHERE id = ? AND estado IN ('EN_PROCESO', 'FALLIDO') AND actualizado_en < ? AND intentos < ?
                RETURNING id, cita_id, intentos
                """, (rs, n) -> new Claimed(rs.getString(1), rs.getString(2), rs.getInt(3)),
                now, reminderId, staleBefore, maxAttempts).stream().findFirst();
    }

    /** EN_PROCESO abandonados que ya agotaron los intentos: quedan FALLIDO definitivo. */
    public int expireExhausted(OffsetDateTime staleBefore, int maxAttempts, OffsetDateTime now) {
        return jdbc.update("""
                UPDATE recordatorios_cita
                SET estado = 'FALLIDO', error_codigo = 'ABANDONADO', actualizado_en = ?, procesado_en = ?
                WHERE estado = 'EN_PROCESO' AND actualizado_en < ? AND intentos >= ?
                """, now, now, staleBefore, maxAttempts);
    }

    public Optional<Context> context(String appointmentId) {
        return jdbc.query("""
                SELECT c.estado, c.programada_en, cp.id, cp.estado, cp.email_verificado_en IS NOT NULL
                FROM citas c LEFT JOIN cuentas_paciente cp ON cp.paciente_id = c.paciente_id
                WHERE c.id = ?
                """, (rs, n) -> new Context(rs.getString(1), rs.getObject(2, OffsetDateTime.class), rs.getString(3),
                rs.getString(4), rs.getBoolean(5)), appointmentId).stream().findFirst();
    }

    /** Cierra el recordatorio solo si sigue EN_PROCESO (nunca reabre uno terminado). */
    public boolean finish(String reminderId, String status, int attempted, int failed, String errorCode, OffsetDateTime now) {
        return jdbc.update("""
                UPDATE recordatorios_cita
                SET estado = ?, dispositivos_intentados = ?, dispositivos_fallidos = ?, error_codigo = ?,
                    actualizado_en = ?, procesado_en = ?
                WHERE id = ? AND estado = 'EN_PROCESO'
                """, status, attempted, failed, errorCode, now, now, reminderId) == 1;
    }
}
