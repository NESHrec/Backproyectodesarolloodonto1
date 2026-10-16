package com.clinicaserena.push.repository;

import com.clinicaserena.push.sender.PushTarget;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/** Dispositivos push de pacientes. Las búsquedas usan el SHA-256 del token, nunca el token en claro. */
@Repository
public class PushDeviceRepository {

    private final JdbcTemplate jdbc;

    public PushDeviceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Alta idempotente: si el token ya existe (mismo teléfono), se reasigna a la cuenta y la sesión
     * actuales. Devuelve el id del dispositivo.
     */
    public String upsert(String newId, String accountId, String sessionTokenHash, String tokenHash, String token,
                         String platform, OffsetDateTime now) {
        return jdbc.queryForObject("""
                INSERT INTO dispositivos_push_paciente
                    (id, cuenta_paciente_id, sesion_paciente_id, token_hash, token, plataforma, registrado_en, actualizado_en)
                VALUES (?, ?, (SELECT id FROM sesiones_paciente WHERE token_hash = ?), ?, ?, ?, ?, ?)
                ON CONFLICT (token_hash) DO UPDATE SET
                    cuenta_paciente_id = EXCLUDED.cuenta_paciente_id,
                    sesion_paciente_id = EXCLUDED.sesion_paciente_id,
                    plataforma = EXCLUDED.plataforma,
                    actualizado_en = EXCLUDED.actualizado_en
                RETURNING id
                """, String.class, newId, accountId, sessionTokenHash, tokenHash, token, platform, now, now);
    }

    /** Retira el token solo si pertenece a la cuenta. Devuelve el id retirado, si lo hubo. */
    public List<String> deleteOwn(String accountId, String tokenHash) {
        return jdbc.queryForList("""
                DELETE FROM dispositivos_push_paciente WHERE token_hash = ? AND cuenta_paciente_id = ? RETURNING id
                """, String.class, tokenHash, accountId);
    }

    /** Al cerrar sesión: retira los dispositivos que registró esa sesión y devuelve sus ids. */
    public List<String> deleteBySessionTokenHash(String sessionTokenHash) {
        return jdbc.queryForList("""
                DELETE FROM dispositivos_push_paciente
                WHERE sesion_paciente_id = (SELECT id FROM sesiones_paciente WHERE token_hash = ?)
                RETURNING id
                """, String.class, sessionTokenHash);
    }

    /** Al revocar todas las sesiones (restablecer contraseña): retira todos los dispositivos de la cuenta. */
    public int deleteByAccount(String accountId) {
        return jdbc.update("DELETE FROM dispositivos_push_paciente WHERE cuenta_paciente_id = ?", accountId);
    }

    public int deleteById(String deviceId) {
        return jdbc.update("DELETE FROM dispositivos_push_paciente WHERE id = ?", deviceId);
    }

    public List<PushTarget> targetsForAccount(String accountId) {
        return jdbc.query("SELECT id, token FROM dispositivos_push_paciente WHERE cuenta_paciente_id = ? ORDER BY registrado_en",
                (rs, n) -> new PushTarget(rs.getString("id"), rs.getString("token")), accountId);
    }
}
