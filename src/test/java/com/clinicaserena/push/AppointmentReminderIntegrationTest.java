package com.clinicaserena.push;

import com.clinicaserena.clinica.ClinicalFixture;
import com.clinicaserena.clinica.MutableClock;
import com.clinicaserena.push.config.PushSettings;
import com.clinicaserena.push.job.AppointmentReminderJob;
import com.clinicaserena.push.repository.PushDeviceRepository;
import com.clinicaserena.push.repository.ReminderRepository;
import com.clinicaserena.push.sender.PushDeliveryResult;
import com.clinicaserena.push.sender.PushMessage;
import com.clinicaserena.push.sender.PushSender;
import com.clinicaserena.push.sender.PushTarget;
import com.clinicaserena.push.sender.SimulatedPushSender;
import com.clinicaserena.push.service.AppointmentReminderService;
import com.clinicaserena.push.service.ReminderMessageFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tarea de recordatorios contra la base aislada: ventana de 24 h, estados, deduplicación entre
 * pasadas, hilos e instancias, umbral stale-after, reintentos, varios dispositivos y privacidad.
 */
@SpringBootTest(properties = {
        "clinica.push.provider=SIMULATED",
        "clinica.push.reminders.enabled=true",
        // La tarea existe, pero el planificador no la dispara durante las pruebas: se llama a mano.
        "clinica.push.reminders.initial-delay-ms=3600000",
        "clinica.push.reminders.interval-ms=3600000"})
@ActiveProfiles("test")
@Import(MutableClock.Config.class)
// Contexto propio (propiedades push): se cierra al terminar para liberar sus conexiones a PostgreSQL.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
class AppointmentReminderIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired MutableClock clock;
    @Autowired AppointmentReminderService service;
    @Autowired SimulatedPushSender simulated;
    @Autowired ReminderRepository reminderRepository;
    @Autowired PushDeviceRepository deviceRepository;
    @Autowired ReminderMessageFactory messages;
    @Autowired PushSettings settings;
    @Autowired ApplicationContext context;

    private ClinicalFixture fixture;
    private PushTestData data;
    private OffsetDateTime now;

    @BeforeEach
    void prepare() {
        fixture = new ClinicalFixture(jdbc);
        fixture.create(passwordEncoder, true);
        data = new PushTestData(jdbc);
        // Las citas del fixture quedan a +1 día del instante real; el reloj de la prueba va 10 días
        // después para que nunca entren en la ventana. Las citas de cada prueba se crean relativas a él.
        now = OffsetDateTime.now(ZoneOffset.UTC).plusDays(10).withNano(0);
        clock.set(now.toInstant());
        simulated.clear();
    }

    @AfterEach
    void cleanup() {
        data.cleanup();
        fixture.cleanup();
    }

    private String status(String appointmentId) {
        return jdbc.queryForObject("SELECT estado FROM recordatorios_cita WHERE cita_id = ?", String.class, appointmentId);
    }

    private int reminders(String appointmentId) {
        return data.count("SELECT count(*) FROM recordatorios_cita WHERE cita_id = ?", appointmentId);
    }

    private AppointmentReminderService otherInstance(PushSender sender) {
        return new AppointmentReminderService(reminderRepository, deviceRepository, messages, sender, settings, clock);
    }

    @Test
    void laTareaExisteSoloConLaPropiedadYLaVentanaRespetaSusBordes() {
        assertThat(context.getBeansOfType(AppointmentReminderJob.class)).hasSize(1);
        data.device(PushTestData.ACCOUNT_X, "SIMULADO-ventana-0000000001", now);
        String outBefore = data.appointment(ClinicalFixture.PATIENT_X, now.plusHours(22).minusSeconds(1), "PENDIENTE");
        String atLowerEdge = data.appointment(ClinicalFixture.PATIENT_X, now.plusHours(22), "PENDIENTE");
        String inside = data.appointment(ClinicalFixture.PATIENT_X, now.plusHours(22).plusSeconds(1), "CONFIRMADA");
        String atUpperEdge = data.appointment(ClinicalFixture.PATIENT_X, now.plusHours(24), "PENDIENTE");
        String outAfter = data.appointment(ClinicalFixture.PATIENT_X, now.plusHours(24).plusSeconds(1), "PENDIENTE");

        service.runOnce();

        assertThat(reminders(outBefore)).as("+21:59:59").isZero();
        assertThat(reminders(atLowerEdge)).as("+22:00 exacto queda fuera: (24h−gracia, 24h]").isZero();
        assertThat(status(inside)).isEqualTo("SIMULADO");
        assertThat(status(atUpperEdge)).as("+24:00 exacto entra").isEqualTo("SIMULADO");
        assertThat(reminders(outAfter)).isZero();
        assertThat(simulated.deliveries()).hasSize(2);
    }

    @Test
    void citasCanceladasCompletadasOPasadasNoSeRecuerdan() {
        data.device(PushTestData.ACCOUNT_X, "SIMULADO-estados-000000000001", now);
        String cancelled = data.appointment(ClinicalFixture.PATIENT_X, now.plusHours(23), "CANCELADA");
        String completed = data.appointment(ClinicalFixture.PATIENT_X, now.plusHours(23).plusMinutes(10), "COMPLETADA");
        String past = data.appointment(ClinicalFixture.PATIENT_X, now.minusHours(1), "PENDIENTE");

        service.runOnce();

        assertThat(reminders(cancelled) + reminders(completed) + reminders(past)).isZero();
        assertThat(simulated.deliveries()).isEmpty();
    }

    @Test
    void cuentaBloqueadaOSinVerificarSeOmiteYSinDispositivosQuedaRegistrado() {
        String noDevice = data.appointment(ClinicalFixture.PATIENT_Y, now.plusHours(23), "PENDIENTE");
        String blocked = data.appointment(ClinicalFixture.PATIENT_X, now.plusHours(23).plusMinutes(5), "PENDIENTE");
        data.device(PushTestData.ACCOUNT_X, "SIMULADO-bloqueada-00000000001", now);
        jdbc.update("UPDATE cuentas_paciente SET estado = 'BLOQUEADA' WHERE id = ?", PushTestData.ACCOUNT_X);

        service.runOnce();

        assertThat(status(noDevice)).isEqualTo("SIN_DISPOSITIVO");
        assertThat(status(blocked)).isEqualTo("OMITIDO");
        assertThat(jdbc.queryForObject("SELECT error_codigo FROM recordatorios_cita WHERE cita_id = ?", String.class, blocked))
                .isEqualTo("CUENTA_NO_APLICA");
        assertThat(simulated.deliveries()).isEmpty();
    }

    @Test
    void variasPasadasYUnReinicioNoDuplican() {
        data.device(PushTestData.ACCOUNT_X, "SIMULADO-dedup-00000000000001", now);
        String appointment = data.appointment(ClinicalFixture.PATIENT_X, now.plusHours(23), "PENDIENTE");

        service.runOnce();
        service.runOnce();
        clock.set(now.plusMinutes(30).toInstant());
        service.runOnce();
        // "Reinicio": otra instancia nueva del servicio con la misma base.
        otherInstance(simulated).runOnce();

        assertThat(reminders(appointment)).isEqualTo(1);
        assertThat(status(appointment)).isEqualTo("SIMULADO");
        assertThat(simulated.deliveries()).hasSize(1);
    }

    @Test
    void dosHilosSimultaneosEnvianUnaSolaVez() throws Exception {
        data.device(PushTestData.ACCOUNT_X, "SIMULADO-hilos-00000000000001", now);
        String appointment = data.appointment(ClinicalFixture.PATIENT_X, now.plusHours(23), "PENDIENTE");
        AppointmentReminderService other = otherInstance(simulated);
        CyclicBarrier start = new CyclicBarrier(2);

        CompletableFuture<?> a = CompletableFuture.runAsync(() -> { await(start); service.runOnce(); });
        CompletableFuture<?> b = CompletableFuture.runAsync(() -> { await(start); other.runOnce(); });
        CompletableFuture.allOf(a, b).get(30, TimeUnit.SECONDS);

        assertThat(reminders(appointment)).isEqualTo(1);
        assertThat(simulated.deliveries()).hasSize(1);
    }

    @Test
    void unEnProcesoRecienteNuncaSeRetomaYNoHayBloqueosDuranteElEnvio() throws Exception {
        data.device(PushTestData.ACCOUNT_X, "SIMULADO-instancias-0000000001", now);
        String appointment = data.appointment(ClinicalFixture.PATIENT_X, now.plusHours(23), "PENDIENTE");
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger sentByA = new AtomicInteger();
        PushSender slow = (target, message) -> {
            sending.countDown();
            await(release);
            sentByA.incrementAndGet();
            return PushDeliveryResult.simulated();
        };
        AppointmentReminderService instanceA = otherInstance(slow);

        CompletableFuture<AppointmentReminderService.RunSummary> a = CompletableFuture.supplyAsync(instanceA::runOnce);
        assertThat(sending.await(10, TimeUnit.SECONDS)).as("A está enviando").isTrue();
        assertThat(status(appointment)).isEqualTo("EN_PROCESO");

        // Mientras A envía, no hay bloqueos abiertos: otra conexión actualiza la cita sin esperar.
        // En UNA misma conexión: si la fila estuviera bloqueada, el UPDATE fallaría por lock_timeout.
        Integer updated = jdbc.execute((ConnectionCallback<Integer>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET lock_timeout = '2s'");
            }
            try (PreparedStatement update = connection.prepareStatement("UPDATE citas SET actualizada_en = actualizada_en WHERE id = ?")) {
                update.setString(1, appointment);
                return update.executeUpdate();
            } finally {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("SET lock_timeout = 0");
                }
            }
        });
        assertThat(updated).isEqualTo(1);

        // B (otra instancia) corre antes del umbral stale-after (10 min): no la retoma.
        clock.set(now.plusMinutes(9).toInstant());
        AppointmentReminderService.RunSummary b = service.runOnce();
        assertThat(b.claimed()).isZero();
        assertThat(b.retaken()).isZero();
        assertThat(simulated.deliveries()).isEmpty();

        release.countDown();
        a.get(10, TimeUnit.SECONDS);
        assertThat(sentByA.get()).isEqualTo(1);
        assertThat(status(appointment)).isEqualTo("SIMULADO");
        assertThat(jdbc.queryForObject("SELECT intentos FROM recordatorios_cita WHERE cita_id = ?", Integer.class, appointment)).isEqualTo(1);
    }

    @Test
    void unEnProcesoAbandonadoSeRetomaUnaVezPasadoElUmbral() {
        data.device(PushTestData.ACCOUNT_X, "SIMULADO-abandonado-000000001", now);
        String recent = data.appointment(ClinicalFixture.PATIENT_X, now.plusHours(23), "PENDIENTE");
        String abandoned = data.appointment(ClinicalFixture.PATIENT_X, now.plusHours(23).plusMinutes(20), "PENDIENTE");
        // Simula una instancia que se cayó a mitad: filas EN_PROCESO de hace 5 y 11 minutos.
        insertInProgress(recent, now.minusMinutes(5));
        insertInProgress(abandoned, now.minusMinutes(11));

        AppointmentReminderService.RunSummary summary = service.runOnce();

        assertThat(summary.retaken()).isEqualTo(1);
        assertThat(status(recent)).as("más reciente que stale-after: nunca se retoma").isEqualTo("EN_PROCESO");
        assertThat(status(abandoned)).isEqualTo("SIMULADO");
        assertThat(jdbc.queryForObject("SELECT intentos FROM recordatorios_cita WHERE cita_id = ?", Integer.class, abandoned)).isEqualTo(2);
        assertThat(simulated.deliveries()).hasSize(1);
    }

    @Test
    void unFallidoSeReintentaHastaTresVecesYQuedaDefinitivo() {
        data.device(PushTestData.ACCOUNT_X, "SIMULADO-falla-00000000000001", now);
        String appointment = data.appointment(ClinicalFixture.PATIENT_X, now.plusHours(23), "PENDIENTE");
        AtomicInteger calls = new AtomicInteger();
        AppointmentReminderService failing = otherInstance((t, m) -> { calls.incrementAndGet(); return PushDeliveryResult.failed("PROVEEDOR_CAIDO"); });

        for (int pass = 0; pass < 5; pass++) {
            clock.set(now.plusMinutes(11L * pass).toInstant());
            failing.runOnce();
        }

        assertThat(calls.get()).isEqualTo(3);
        assertThat(status(appointment)).isEqualTo("FALLIDO");
        assertThat(jdbc.queryForObject("SELECT intentos FROM recordatorios_cita WHERE cita_id = ?", Integer.class, appointment)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT error_codigo FROM recordatorios_cita WHERE cita_id = ?", String.class, appointment))
                .isEqualTo("PROVEEDOR_CAIDO");
    }

    @Test
    void conVariosDispositivosBastaUnoBienYFallidoSoloSiFallanTodos() {
        String good = data.device(PushTestData.ACCOUNT_X, "SIMULADO-bien-000000000000001", now);
        String bad = data.device(PushTestData.ACCOUNT_X, "SIMULADO-mal-0000000000000001", now);
        String invalid = data.device(PushTestData.ACCOUNT_Y, "SIMULADO-invalido-00000000001", now);
        data.device(PushTestData.ACCOUNT_Y, "SIMULADO-mal-0000000000000002", now);
        String partial = data.appointment(ClinicalFixture.PATIENT_X, now.plusHours(23), "PENDIENTE");
        String allFailed = data.appointment(ClinicalFixture.PATIENT_Y, now.plusHours(23).plusMinutes(5), "PENDIENTE");
        PushSender mixed = (target, message) -> target.token().contains("-bien-") ? PushDeliveryResult.simulated()
                : target.token().contains("-invalido-") ? PushDeliveryResult.invalidToken() : PushDeliveryResult.failed("RECHAZADO");

        otherInstance(mixed).runOnce();

        assertThat(status(partial)).isEqualTo("SIMULADO");
        assertThat(data.count("SELECT dispositivos_intentados FROM recordatorios_cita WHERE cita_id = ?", partial)).isEqualTo(2);
        assertThat(data.count("SELECT dispositivos_fallidos FROM recordatorios_cita WHERE cita_id = ?", partial)).isEqualTo(1);
        assertThat(status(allFailed)).isEqualTo("FALLIDO");
        assertThat(data.count("SELECT count(*) FROM dispositivos_push_paciente WHERE id = ?", invalid)).as("token inválido retirado").isZero();
        assertThat(data.count("SELECT count(*) FROM dispositivos_push_paciente WHERE id IN (?, ?)", good, bad)).isEqualTo(2);
    }

    @Test
    void laNotificacionNoLlevaDatosClinicosYElTokenNoApareceEnLogs(CapturedOutput output) {
        String token = "SIMULADO-privacidad-" + "Z".repeat(30);
        data.device(PushTestData.ACCOUNT_X, token, now);
        String appointment = data.appointment(ClinicalFixture.PATIENT_X, now.plusHours(23), "PENDIENTE");

        context.getBean(AppointmentReminderJob.class).run();

        List<SimulatedPushSender.SimulatedDelivery> deliveries = simulated.deliveries();
        assertThat(deliveries).hasSize(1);
        PushMessage message = deliveries.get(0).message();
        String everything = message.title() + message.body() + message.data();
        for (String forbidden : List.of("Profesional uno de prueba", "Especialidad clínica de prueba", "Paciente X sintético",
                "Nota clínica sintética", "patient.x@example.test", token)) {
            assertThat(everything).doesNotContain(forbidden);
        }
        assertThat(message.data()).isEqualTo(Map.of("type", "APPOINTMENT_REMINDER", "appointmentId", appointment));
        assertThat(output.getAll()).contains("Recordatorios de cita: reclamados=1").doesNotContain(token).doesNotContain("Z".repeat(30));
        assertThat(String.valueOf(new PushTarget("d1", token))).doesNotContain(token);
        assertThat(data.count("SELECT count(*) FROM bitacora_eventos WHERE entidad_id LIKE '%' || ? || '%'", "privacidad")).isZero();
    }

    private void insertInProgress(String appointmentId, OffsetDateTime updatedAt) {
        OffsetDateTime at = jdbc.queryForObject("SELECT programada_en FROM citas WHERE id = ?", OffsetDateTime.class, appointmentId);
        jdbc.update("INSERT INTO recordatorios_cita(id,cita_id,tipo,estado,programada_en,intentos,creado_en,actualizado_en) "
                + "VALUES (?,?, 'CITA_24H','EN_PROCESO',?,1,?,?)", java.util.UUID.randomUUID().toString(), appointmentId, at, updatedAt, updatedAt);
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("tiempo agotado");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
