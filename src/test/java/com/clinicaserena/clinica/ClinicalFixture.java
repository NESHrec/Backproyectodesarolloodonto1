package com.clinicaserena.clinica;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Datos sintéticos aislados por prefijo de identificador para las pruebas clínicas. */
public final class ClinicalFixture {

    public static final String PASSWORD = "ClinicalTestPassword!2026";

    public static final String ADMIN = "98000000-0000-0000-0000-000000000001";
    public static final String RECEPTION = "98000000-0000-0000-0000-000000000002";
    public static final String DOCTOR_ONE = "98000000-0000-0000-0000-000000000003";
    public static final String DOCTOR_TWO = "98000000-0000-0000-0000-000000000004";
    public static final String DOCTOR_UNLINKED = "98000000-0000-0000-0000-000000000005";

    public static final String SPECIALTY = "98100000-0000-0000-0000-000000000001";
    public static final String PRACTITIONER_ONE = "98200000-0000-0000-0000-000000000001";
    public static final String PRACTITIONER_TWO = "98200000-0000-0000-0000-000000000002";
    public static final String PRACTITIONER_FREE = "98200000-0000-0000-0000-000000000003";

    public static final String PATIENT_X = "98300000-0000-0000-0000-000000000001";
    public static final String PATIENT_Y = "98300000-0000-0000-0000-000000000002";

    /** Citas: uno=profesional uno, dos=profesional dos. */
    public static final String APPT_X_ONE_PENDING = "98500000-0000-0000-0000-000000000001";
    public static final String APPT_Y_ONE_CONFIRMED = "98500000-0000-0000-0000-000000000002";
    public static final String APPT_X_TWO_PENDING = "98500000-0000-0000-0000-000000000003";
    public static final String APPT_X_ONE_CANCELLED = "98500000-0000-0000-0000-000000000004";
    public static final String APPT_Y_ONE_COMPLETED = "98500000-0000-0000-0000-000000000005";
    public static final String APPT_X_ONE_CONCURRENT = "98500000-0000-0000-0000-000000000006";
    /** Llegada registrada, pero su hora programada es posterior al instante de las pruebas. */
    public static final String APPT_X_ONE_FUTURE = "98500000-0000-0000-0000-000000000007";
    /** Hora ya comenzada, pero recepción no ha registrado la llegada. */
    public static final String APPT_Y_ONE_NO_ARRIVAL = "98500000-0000-0000-0000-000000000008";

    /** Desfases respecto de {@link #base()}; las pruebas fijan el reloj en base + 6 h. */
    public static final long FUTURE_OFFSET_HOURS = 8;
    public static final long CLOCK_OFFSET_HOURS = 6;

    private static final List<String> STAFF = List.of(ADMIN, RECEPTION, DOCTOR_ONE, DOCTOR_TWO, DOCTOR_UNLINKED);
    private static final List<String> APPOINTMENTS = List.of(APPT_X_ONE_PENDING, APPT_Y_ONE_CONFIRMED,
            APPT_X_TWO_PENDING, APPT_X_ONE_CANCELLED, APPT_Y_ONE_COMPLETED, APPT_X_ONE_CONCURRENT,
            APPT_X_ONE_FUTURE, APPT_Y_ONE_NO_ARRIVAL);

    private final JdbcTemplate jdbc;
    private OffsetDateTime base;

    public ClinicalFixture(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void create(PasswordEncoder encoder, boolean linkDoctors) {
        cleanup();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String hash = encoder.encode(PASSWORD);
        staff(ADMIN, "admin.clinical@example.test", "Admin clínico de prueba", "ADMIN", hash, now);
        staff(RECEPTION, "reception.clinical@example.test", "Recepción clínica de prueba", "RECEPCION", hash, now);
        staff(DOCTOR_ONE, "doctor.one@example.test", "Médico uno de prueba", "MEDICO", hash, now);
        staff(DOCTOR_TWO, "doctor.two@example.test", "Médico dos de prueba", "MEDICO", hash, now);
        staff(DOCTOR_UNLINKED, "doctor.unlinked@example.test", "Médico sin vínculo de prueba", "MEDICO", hash, now);

        jdbc.update("INSERT INTO especialidades(id,nombre,descripcion) VALUES (?,?,?)",
                SPECIALTY, "Especialidad clínica de prueba", "Solo fixture");
        practitioner(PRACTITIONER_ONE, "Profesional uno de prueba");
        practitioner(PRACTITIONER_TWO, "Profesional dos de prueba");
        practitioner(PRACTITIONER_FREE, "Profesional libre de prueba");
        if (linkDoctors) {
            link(DOCTOR_ONE, PRACTITIONER_ONE, now);
            link(DOCTOR_TWO, PRACTITIONER_TWO, now);
        }

        patient(PATIENT_X, "patient.x@example.test", "Paciente X sintético", now);
        patient(PATIENT_Y, "patient.y@example.test", "Paciente Y sintético", now);

        base = now.plusDays(1).withNano(0);
        appointment(APPT_X_ONE_PENDING, PATIENT_X, PRACTITIONER_ONE, base, "PENDIENTE", now);
        appointment(APPT_Y_ONE_CONFIRMED, PATIENT_Y, PRACTITIONER_ONE, base.plusHours(1), "CONFIRMADA", now);
        appointment(APPT_X_TWO_PENDING, PATIENT_X, PRACTITIONER_TWO, base.plusHours(2), "PENDIENTE", now);
        appointment(APPT_X_ONE_CANCELLED, PATIENT_X, PRACTITIONER_ONE, base.plusHours(3), "CANCELADA", now);
        appointment(APPT_Y_ONE_COMPLETED, PATIENT_Y, PRACTITIONER_ONE, base.plusHours(4), "COMPLETADA", now);
        appointment(APPT_X_ONE_CONCURRENT, PATIENT_X, PRACTITIONER_ONE, base.plusHours(5), "PENDIENTE", now);
        appointment(APPT_X_ONE_FUTURE, PATIENT_X, PRACTITIONER_ONE, base.plusHours(FUTURE_OFFSET_HOURS), "PENDIENTE", now);
        appointment(APPT_Y_ONE_NO_ARRIVAL, PATIENT_Y, PRACTITIONER_ONE, base.plusMinutes(30), "CONFIRMADA", now);
        for (String appointment : List.of(APPT_X_ONE_PENDING, APPT_Y_ONE_CONFIRMED, APPT_X_TWO_PENDING,
                APPT_X_ONE_CONCURRENT, APPT_X_ONE_FUTURE)) {
            registerArrival(appointment, base.minusMinutes(10));
        }
    }

    /** Instante de referencia de las citas; se asigna en {@link #create}. */
    public OffsetDateTime base() {
        return base;
    }

    public void registerArrival(String appointment, OffsetDateTime at) {
        jdbc.update("UPDATE citas SET llegada_en = ?, llegada_por_personal_id = ? WHERE id = ?", at, RECEPTION, appointment);
    }

    public void cleanup() {
        String staffIn = in(STAFF);
        String appointmentsIn = in(APPOINTMENTS);
        // V10 protege el historial contra UPDATE y DELETE. Las pruebas usan una base
        // aislada, por lo que vaciamos las dos tablas completas sin debilitar los triggers.
        jdbc.execute("TRUNCATE TABLE intenciones_pago_recepcion, pagos_citas, cargos_citas_auditoria");
        // La inmutabilidad bloquea DELETE fila a fila. Solo el fixture, sobre la base
        // aislada de pruebas, usa TRUNCATE para reiniciar datos sintéticos entre casos.
        jdbc.execute("DO $$ BEGIN IF to_regclass('adendas_atencion') IS NOT NULL THEN "
                + "EXECUTE 'TRUNCATE TABLE adendas_atencion, versiones_perfil_clinico, receta_items, atenciones_clinicas, expedientes_clinicos'; "
                + "ELSE EXECUTE 'TRUNCATE TABLE receta_items, atenciones_clinicas, expedientes_clinicos'; END IF; END $$");
        jdbc.update("DELETE FROM historial_vinculacion_medico WHERE cuenta_personal_id IN " + staffIn, STAFF.toArray());
        jdbc.update("DELETE FROM sesiones_personal WHERE cuenta_id IN " + staffIn, STAFF.toArray());
        jdbc.update("UPDATE cuentas_personal SET medico_id = NULL, medico_vinculado_en = NULL, medico_vinculado_por = NULL "
                + "WHERE id IN " + staffIn, STAFF.toArray());
        jdbc.update("UPDATE citas SET llegada_en = NULL, llegada_por_personal_id = NULL WHERE id IN " + appointmentsIn,
                APPOINTMENTS.toArray());
        jdbc.update("DELETE FROM citas WHERE id IN " + appointmentsIn, APPOINTMENTS.toArray());
        jdbc.update("DELETE FROM cuentas_personal WHERE id IN " + staffIn, STAFF.toArray());
        jdbc.update("DELETE FROM bloques_disponibilidad WHERE medico_id IN (?, ?, ?)",
                PRACTITIONER_ONE, PRACTITIONER_TWO, PRACTITIONER_FREE);
        jdbc.update("DELETE FROM medicos WHERE id IN (?, ?, ?)", PRACTITIONER_ONE, PRACTITIONER_TWO, PRACTITIONER_FREE);
        jdbc.update("DELETE FROM especialidades WHERE id = ?", SPECIALTY);
        jdbc.update("DELETE FROM cuentas_paciente WHERE paciente_id IN (?, ?)", PATIENT_X, PATIENT_Y);
        jdbc.update("DELETE FROM pacientes WHERE id IN (?, ?)", PATIENT_X, PATIENT_Y);
    }

    public static String login(MockMvc mockMvc, ObjectMapper objectMapper, String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/staff/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private void staff(String id, String email, String name, String role, String hash, OffsetDateTime now) {
        jdbc.update("INSERT INTO cuentas_personal(id,email_normalizado,nombre_completo,password_hash,rol,estado,creado_en,actualizada_en) "
                + "VALUES (?,?,?,?,?,'ACTIVA',?,?)", id, email, name, hash, role, now, now);
    }

    private void practitioner(String id, String name) {
        jdbc.update("INSERT INTO medicos(id,nombre_completo,especialidad_id,numero_colegiado) VALUES (?,?,?,?)",
                id, name, SPECIALTY, "CLIN-" + id);
    }

    private void link(String account, String practitioner, OffsetDateTime now) {
        jdbc.update("UPDATE cuentas_personal SET medico_id = ?, medico_vinculado_en = ?, medico_vinculado_por = ? WHERE id = ?",
                practitioner, now, ADMIN, account);
    }

    private void patient(String id, String email, String name, OffsetDateTime now) {
        jdbc.update("INSERT INTO pacientes(id,estado,creado_en) VALUES (?, 'ACTIVO', ?)", id, now);
        jdbc.update("INSERT INTO cuentas_paciente(id,paciente_id,email_normalizado,password_hash,estado,creado_en,actualizada_en,email_verificado_en,nombre_completo) "
                + "VALUES (?,?,?,?,'ACTIVA',?,?,?,?)", id.replace("98300000", "98400000"), id, email,
                "$2a$10$7SVF2Ae33PSty.ijWwytpuz43k/lWcvX3vNyNajyRggg7Bg/CjtTO", now, now, now, name);
    }

    private void appointment(String id, String patient, String practitioner, OffsetDateTime at, String state,
                             OffsetDateTime now) {
        String block = id.replace("98500000", "98600000");
        jdbc.update("INSERT INTO bloques_disponibilidad(id,medico_id,inicio,fin,disponible) VALUES (?,?,?,?,false)",
                block, practitioner, at, at.plusMinutes(30));
        jdbc.update("INSERT INTO citas(id,paciente_id,bloque_id,medico_id,especialidad_id,programada_en,estado,creada_en,actualizada_en) "
                + "VALUES (?,?,?,?,?,?,?,?,?)", id, patient, block, practitioner, SPECIALTY, at, state, now, now);
    }

    private static String in(List<String> values) {
        return "(" + String.join(",", values.stream().map(value -> "?").toList()) + ")";
    }

    private static Object[] concat(List<String> values, String... extra) {
        Object[] result = new Object[values.size() + extra.length];
        for (int index = 0; index < values.size(); index++) result[index] = values.get(index);
        System.arraycopy(extra, 0, result, values.size(), extra.length);
        return result;
    }
}
