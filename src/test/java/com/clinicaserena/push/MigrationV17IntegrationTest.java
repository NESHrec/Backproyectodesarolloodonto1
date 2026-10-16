package com.clinicaserena.push;

import com.clinicaserena.auth.security.TokenHasher;
import com.clinicaserena.clinica.ClinicalFixture;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.OffsetDateTime;
import java.util.UUID;

import static com.clinicaserena.clinica.ClinicalFixture.APPT_X_ONE_PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V17 sobre una base con datos en V16: no altera lo existente y sus restricciones se cumplen. */
class MigrationV17IntegrationTest {

    @Test
    void migraDesdeV16SinAlterarDatosYConRestricciones() {
        String host = required("TEST_DB_HOST");
        String port = required("TEST_DB_PORT");
        String database = required("TEST_DB_NAME");
        String schema = "migration_v17_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:postgresql://" + host + ":" + port + "/" + database + "?currentSchema=" + schema;
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, required("TEST_DB_USER"), required("TEST_DB_PASSWORD"));
        Flyway v16 = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).createSchemas(true).target("16").load();
        try {
            assertThat(v16.migrate().success).isTrue();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            ClinicalFixture fixture = new ClinicalFixture(jdbc);
            fixture.create(new BCryptPasswordEncoder(), true);
            int appointmentsBefore = count(jdbc, "SELECT count(*) FROM citas");
            int accountsBefore = count(jdbc, "SELECT count(*) FROM cuentas_paciente");

            assertThat(Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load().migrate().success).isTrue();

            assertThat(count(jdbc, "SELECT count(*) FROM flyway_schema_history WHERE version = '17'")).isEqualTo(1);
            assertThat(count(jdbc, "SELECT count(*) FROM citas")).isEqualTo(appointmentsBefore);
            assertThat(count(jdbc, "SELECT count(*) FROM cuentas_paciente")).isEqualTo(accountsBefore);
            assertThat(count(jdbc, "SELECT count(*) FROM recordatorios_cita")).isZero();
            assertThat(count(jdbc, "SELECT count(*) FROM dispositivos_push_paciente")).isZero();

            OffsetDateTime now = OffsetDateTime.now();
            String account = PushTestData.ACCOUNT_X;
            String token = "SIMULADO-migracion-0000000001";
            String insertDevice = "INSERT INTO dispositivos_push_paciente(id,cuenta_paciente_id,token_hash,token,plataforma,registrado_en,actualizado_en) VALUES (?,?,?,?,?,?,?)";
            jdbc.update(insertDevice, UUID.randomUUID().toString(), account, TokenHasher.sha256(token), token, "ANDROID", now, now);
            assertThatThrownBy(() -> jdbc.update(insertDevice, UUID.randomUUID().toString(), account, TokenHasher.sha256(token), token, "ANDROID", now, now))
                    .as("token único").isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update(insertDevice, UUID.randomUUID().toString(), account, TokenHasher.sha256("otro-token-valido-123"), "otro-token-valido-123", "IOS", now, now))
                    .as("plataforma").isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update(insertDevice, UUID.randomUUID().toString(), account, TokenHasher.sha256("corto"), "corto", "ANDROID", now, now))
                    .as("largo del token").isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update(insertDevice, UUID.randomUUID().toString(), account, "NO-ES-UN-HASH", "token-de-prueba-valido-2", "ANDROID", now, now))
                    .as("formato del hash").isInstanceOf(DataIntegrityViolationException.class);

            String insertReminder = "INSERT INTO recordatorios_cita(id,cita_id,tipo,estado,programada_en,intentos,creado_en,actualizado_en) VALUES (?,?,?,?,?,?,?,?)";
            jdbc.update(insertReminder, UUID.randomUUID().toString(), APPT_X_ONE_PENDING, "CITA_24H", "EN_PROCESO", now, 1, now, now);
            assertThatThrownBy(() -> jdbc.update(insertReminder, UUID.randomUUID().toString(), APPT_X_ONE_PENDING, "CITA_24H", "EN_PROCESO", now, 1, now, now))
                    .as("un recordatorio por cita y tipo").isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update(insertReminder, UUID.randomUUID().toString(), ClinicalFixture.APPT_Y_ONE_CONFIRMED, "CITA_24H", "ENTREGADO", now, 1, now, now))
                    .as("estado").isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update(insertReminder, UUID.randomUUID().toString(), ClinicalFixture.APPT_Y_ONE_CONFIRMED, "CITA_1H", "EN_PROCESO", now, 1, now, now))
                    .as("tipo").isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("UPDATE recordatorios_cita SET dispositivos_intentados = 1, dispositivos_fallidos = 2"))
                    .as("fallidos <= intentados").isInstanceOf(DataIntegrityViolationException.class);

            // ON DELETE: borrar la cuenta borra sus dispositivos (como sus sesiones).
            jdbc.update("DELETE FROM recordatorios_cita");
            jdbc.update("DELETE FROM cuentas_paciente WHERE id = ?", account);
            assertThat(count(jdbc, "SELECT count(*) FROM dispositivos_push_paciente")).isZero();
        } finally {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).cleanDisabled(false).load().clean();
        }
    }

    private static int count(JdbcTemplate jdbc, String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required; tests must use an isolated PostgreSQL database");
        }
        return value;
    }
}
