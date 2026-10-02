package com.clinicaserena.citas.service;

import com.clinicaserena.catalogo.service.CatalogoService;
import com.clinicaserena.citas.dto.CrearCitaRequest;
import com.clinicaserena.clinica.MutableClock;
import com.clinicaserena.common.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(MutableClock.Config.class)
class CitaServiceIntegrationTest {

    private static final String PATIENT_ONE = "90000000-0000-0000-0000-000000000011";
    private static final String PATIENT_TWO = "90000000-0000-0000-0000-000000000012";

    @Autowired CitaService citaService;
    @Autowired CatalogoService catalogoService;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;

    private String especialidadId;
    private String medicoId;
    private String bloqueId;
    private OffsetDateTime inicio;

    @BeforeEach
    void prepararPacientes() {
        clock.set(OffsetDateTime.of(2026, 10, 1, 15, 0, 0, 0, ZoneOffset.UTC).toInstant());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("INSERT INTO pacientes(id, estado, creado_en) VALUES (?, 'ACTIVO', ?)", PATIENT_ONE, now);
        jdbc.update("INSERT INTO pacientes(id, estado, creado_en) VALUES (?, 'ACTIVO', ?)", PATIENT_TWO, now);
    }

    @AfterEach
    void limpiar() {
        if (bloqueId != null) {
            jdbc.update("DELETE FROM citas WHERE bloque_id = ?", bloqueId);
            jdbc.update("DELETE FROM bloques_disponibilidad WHERE id = ?", bloqueId);
        }
        if (medicoId != null) jdbc.update("DELETE FROM medicos WHERE id = ?", medicoId);
        if (especialidadId != null) jdbc.update("DELETE FROM especialidades WHERE id = ?", especialidadId);
        jdbc.update("DELETE FROM pacientes WHERE id IN (?, ?)", PATIENT_ONE, PATIENT_TWO);
    }

    @Test
    void reservaValidaPersisteCitaYRetiraBloqueDeDisponibilidadPublica() {
        prepararBloque(true);

        var cita = citaService.reservar(PATIENT_ONE, request(null));

        assertThat(cita.practitionerId()).isEqualTo(medicoId);
        assertThat(cita.scheduledAt()).isEqualTo(inicio);
        assertThat(jdbc.queryForObject("SELECT disponible FROM bloques_disponibilidad WHERE id = ?", Boolean.class, bloqueId)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM citas WHERE bloque_id = ?", Integer.class, bloqueId)).isOne();
        var visibles = catalogoService.obtenerDisponibilidad(medicoId, inicio.toLocalDate(), inicio.toLocalDate());
        assertThat(visibles).noneMatch(slot -> slot.id().equals(bloqueId));
    }

    @Test
    void rechazaBloqueInexistenteNoDisponibleYOtroProfesional() {
        prepararBloque(false);
        assertThatThrownBy(() -> citaService.reservar(PATIENT_ONE, request(null)))
                .isInstanceOf(ApiException.class).hasMessage("El bloque ya no está disponible");

        var otro = new CrearCitaRequest("medico-inexistente", especialidadId, inicio, null);
        assertThatThrownBy(() -> citaService.reservar("patient-test-1", otro))
                .isInstanceOf(ApiException.class).hasMessage("El bloque no existe");
    }

    @Test
    void dosSolicitudesConcurrentesProducenUnaSolaCita() throws Exception {
        prepararBloque(true);
        CountDownLatch inicioComun = new CountDownLatch(1);
        AtomicInteger exitos = new AtomicInteger();
        AtomicInteger conflictos = new AtomicInteger();

        CompletableFuture<Void> primera = reservarConcurrente(PATIENT_ONE, inicioComun, exitos, conflictos);
        CompletableFuture<Void> segunda = reservarConcurrente(PATIENT_TWO, inicioComun, exitos, conflictos);
        inicioComun.countDown();
        CompletableFuture.allOf(primera, segunda).get(10, TimeUnit.SECONDS);

        assertThat(exitos).hasValue(1);
        assertThat(conflictos).hasValue(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM citas WHERE bloque_id = ?", Integer.class, bloqueId)).isOne();
    }

    @Test
    void cancelarAntesDeLaFechaLiberaElBloqueYPermiteVolverAReservar() {
        prepararBloque(true);
        var primera = citaService.reservar(PATIENT_ONE, request(null));

        var cancelada = citaService.cancelar(PATIENT_ONE, primera.id());

        assertThat(cancelada.status().name()).isEqualTo("CANCELADA");
        assertThat(jdbc.queryForObject("SELECT disponible FROM bloques_disponibilidad WHERE id = ?", Boolean.class, bloqueId)).isTrue();
        assertThat(catalogoService.obtenerDisponibilidad(medicoId, inicio.toLocalDate(), inicio.toLocalDate()))
                .anyMatch(slot -> slot.id().equals(bloqueId));

        var segunda = citaService.reservar(PATIENT_TWO, request(null));

        assertThat(segunda.id()).isNotEqualTo(primera.id());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM citas WHERE bloque_id = ? AND estado = 'CANCELADA'", Integer.class, bloqueId)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM citas WHERE bloque_id = ? AND estado <> 'CANCELADA'", Integer.class, bloqueId)).isOne();
        assertThat(jdbc.queryForObject("SELECT disponible FROM bloques_disponibilidad WHERE id = ?", Boolean.class, bloqueId)).isFalse();
    }

    @Test
    void fallaDeInsercionRevierteCitaYDisponibilidad() {
        prepararBloque(true);
        jdbc.execute("ALTER TABLE citas ADD CONSTRAINT ck_test_rollback CHECK (notas <> 'FORCE_ROLLBACK')");
        try {
            assertThatThrownBy(() -> citaService.reservar(PATIENT_ONE, request("FORCE_ROLLBACK")))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            jdbc.execute("ALTER TABLE citas DROP CONSTRAINT IF EXISTS ck_test_rollback");
        }

        assertThat(jdbc.queryForObject("SELECT disponible FROM bloques_disponibilidad WHERE id = ?", Boolean.class, bloqueId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM citas WHERE bloque_id = ?", Integer.class, bloqueId)).isZero();
    }

    @Test
    void validaIdentidadYDatosAntesDePersistir() {
        prepararBloque(true);
        assertThatThrownBy(() -> citaService.reservar(" ", request(null)))
                .isInstanceOf(ApiException.class).hasMessage("La identidad del paciente no es válida");
        assertThatThrownBy(() -> citaService.reservar(PATIENT_ONE, null))
                .isInstanceOf(ApiException.class).hasMessage("Los datos de la cita son obligatorios");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM citas WHERE bloque_id = ?", Integer.class, bloqueId)).isZero();
    }

    @Test
    void rechazaReservaSobreBloquePasadoSinModificarElBloque() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime past = now.minusMinutes(1);
        prepararBloqueAt(past, true);
        String historicalAppointment = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO citas(id,paciente_id,bloque_id,medico_id,especialidad_id,programada_en,estado,creada_en,actualizada_en) VALUES (?,?,?,?,?,?,'COMPLETADA',?,?)",
                historicalAppointment, PATIENT_ONE, bloqueId, medicoId, especialidadId, past,
                now.minusDays(1), now.minusDays(1));

        assertThatThrownBy(() -> citaService.reservar(PATIENT_ONE, request(null)))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException apiError = (ApiException) error;
                    assertThat(apiError.getCode()).isEqualTo("SLOT_IN_PAST");
                    assertThat(apiError.getStatus().value()).isEqualTo(400);
                });
        assertThat(jdbc.queryForObject("SELECT disponible FROM bloques_disponibilidad WHERE id = ?", Boolean.class, bloqueId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM citas WHERE bloque_id = ?", Integer.class, bloqueId)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM citas WHERE id = ? AND estado = 'COMPLETADA'", Integer.class, historicalAppointment)).isOne();
    }

    @Test
    void avanzarSoloElClockExcluyeElBloqueYRechazaLaReserva() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        prepararBloqueAt(now.plusHours(1), true);
        assertThat(catalogoService.obtenerDisponibilidad(medicoId, now.toLocalDate(), now.toLocalDate()))
                .anyMatch(slot -> slot.id().equals(bloqueId));

        clock.set(now.plusHours(2).toInstant());
        assertThat(catalogoService.obtenerDisponibilidad(medicoId, now.toLocalDate(), now.toLocalDate()))
                .noneMatch(slot -> slot.id().equals(bloqueId));
        assertThatThrownBy(() -> citaService.reservar(PATIENT_ONE, request(null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("No se puede reservar un bloque cuyo inicio ya pasó");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bloques_disponibilidad WHERE id = ?", Integer.class, bloqueId)).isOne();
        assertThat(jdbc.queryForObject("SELECT disponible FROM bloques_disponibilidad WHERE id = ?", Boolean.class, bloqueId)).isTrue();
    }

    private CompletableFuture<Void> reservarConcurrente(
            String paciente, CountDownLatch latch, AtomicInteger exitos, AtomicInteger conflictos) {
        return CompletableFuture.runAsync(() -> {
            try {
                latch.await();
                citaService.reservar(paciente, request(null));
                exitos.incrementAndGet();
            } catch (ApiException ex) {
                if ("SLOT_NOT_AVAILABLE".equals(ex.getCode())) conflictos.incrementAndGet();
                else throw ex;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
        });
    }

    private CrearCitaRequest request(String notas) {
        return new CrearCitaRequest(medicoId, especialidadId, inicio, notas);
    }

    private void prepararBloque(boolean disponible) {
        prepararBloqueAt(OffsetDateTime.now(clock).plusDays(20).withNano(0), disponible);
    }

    private void prepararBloqueAt(OffsetDateTime scheduledAt, boolean disponible) {
        especialidadId = UUID.randomUUID().toString();
        medicoId = UUID.randomUUID().toString();
        bloqueId = UUID.randomUUID().toString();
        inicio = scheduledAt;
        jdbc.update("INSERT INTO especialidades(id, nombre, descripcion) VALUES (?, ?, ?)",
                especialidadId, "Especialidad " + especialidadId, "Dato ficticio de prueba");
        jdbc.update("INSERT INTO medicos(id, nombre_completo, especialidad_id, numero_colegiado) VALUES (?, ?, ?, ?)",
                medicoId, "Profesional ficticio", especialidadId, "TEST-" + medicoId);
        jdbc.update("INSERT INTO bloques_disponibilidad(id, medico_id, inicio, fin, disponible) VALUES (?, ?, ?, ?, ?)",
                bloqueId, medicoId, inicio, inicio.plusMinutes(30), disponible);
    }
}
