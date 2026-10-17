package com.clinicaserena.push;

import com.clinicaserena.auth.security.TokenHasher;
import com.clinicaserena.clinica.ClinicalFixture;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Datos sintéticos para las pruebas push, sobre el {@link ClinicalFixture} (base aislada de pruebas). */
final class PushTestData {

    static final String ACCOUNT_X = ClinicalFixture.PATIENT_X.replace("98300000", "98400000");
    static final String ACCOUNT_Y = ClinicalFixture.PATIENT_Y.replace("98300000", "98400000");

    private final JdbcTemplate jdbc;
    private final List<String> appointments = new ArrayList<>();

    PushTestData(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Sesión de paciente válida (Bearer opaco) con vencimiento lejano al reloj de la prueba. */
    String patientToken(String accountId, OffsetDateTime now) {
        String raw = "push-test-" + UUID.randomUUID();
        jdbc.update("INSERT INTO sesiones_paciente(id,cuenta_id,token_hash,expira_en,creada_en) VALUES (?,?,?,?,?)",
                UUID.randomUUID().toString(), accountId, TokenHasher.sha256(raw), now.plusDays(30), now.minusMinutes(1));
        return raw;
    }

    /** Cita sintética con su propio bloque. Se borra en {@link #cleanup()}. */
    String appointment(String patientId, OffsetDateTime at, String state) {
        String id = "98700000-0000-0000-0000-" + String.format("%012d", appointments.size() + 1);
        String block = id.replace("98700000", "98800000");
        OffsetDateTime created = at.minusDays(3);
        jdbc.update("INSERT INTO bloques_disponibilidad(id,medico_id,inicio,fin,disponible) VALUES (?,?,?,?,false)",
                block, ClinicalFixture.PRACTITIONER_ONE, at, at.plusMinutes(30));
        jdbc.update("INSERT INTO citas(id,paciente_id,bloque_id,medico_id,especialidad_id,programada_en,estado,notas,creada_en,actualizada_en) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?)", id, patientId, block, ClinicalFixture.PRACTITIONER_ONE,
                ClinicalFixture.SPECIALTY, at, state, "Nota clínica sintética que nunca debe salir", created, created);
        appointments.add(id);
        return id;
    }

    /** Dispositivo registrado directamente (sin pasar por la API). */
    String device(String accountId, String token, OffsetDateTime now) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO dispositivos_push_paciente(id,cuenta_paciente_id,token_hash,token,plataforma,registrado_en,actualizado_en) "
                + "VALUES (?,?,?,?,'ANDROID',?,?)", id, accountId, TokenHasher.sha256(token), token, now, now);
        return id;
    }

    int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    void cleanup() {
        jdbc.execute("TRUNCATE TABLE recordatorios_cita, dispositivos_push_paciente");
        for (String id : appointments) {
            jdbc.update("DELETE FROM citas WHERE id = ?", id);
            jdbc.update("DELETE FROM bloques_disponibilidad WHERE id = ?", id.replace("98700000", "98800000"));
        }
        appointments.clear();
        jdbc.update("DELETE FROM sesiones_paciente WHERE cuenta_id IN (?, ?)", ACCOUNT_X, ACCOUNT_Y);
    }
}
