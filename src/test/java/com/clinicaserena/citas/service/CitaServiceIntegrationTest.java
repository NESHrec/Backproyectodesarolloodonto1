package com.clinicaserena.citas.service;

import com.clinicaserena.catalogo.service.CatalogoService;
import com.clinicaserena.citas.dto.CrearCitaRequest;
import com.clinicaserena.common.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

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
class CitaServiceIntegrationTest {

    @Autowired CitaService citaService;
    @Autowired CatalogoService catalogoService;
    @Autowired JdbcTemplate jdbc;

    private String especialidadId;
    private String medicoId;
    private String bloqueId;
    private OffsetDateTime inicio;

    @AfterEach
    void limpiar() {
        if (bloqueId != null) {
            jdbc.update("DELETE FROM citas WHERE bloque_id = ?", bloqueId);
            jdbc.update("DELETE FROM bloques_disponibilidad WHERE id = ?", bloqueId);
        }
        if (medicoId != null) jdbc.update("DELETE FROM medicos WHERE id = ?", medicoId);
        if (especialidadId != null) jdbc.update("DELETE FROM especialidades WHERE id = ?", especialidadId);
    }

    @Test
    void reservaValidaPersisteCitaYRetiraBloqueDeDisponibilidadPublica() {
        prepararBloque(true);

        var cita = citaService.reservar("patient-test-1", request(null));

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
        assertThatThrownBy(() -> citaService.reservar("patient-test-1", request(null)))
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

        CompletableFuture<Void> primera = reservarConcurrente("patient-test-1", inicioComun, exitos, conflictos);
        CompletableFuture<Void> segunda = reservarConcurrente("patient-test-2", inicioComun, exitos, conflictos);
        inicioComun.countDown();
        CompletableFuture.allOf(primera, segunda).get(10, TimeUnit.SECONDS);

        assertThat(exitos).hasValue(1);
        assertThat(conflictos).hasValue(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM citas WHERE bloque_id = ?", Integer.class, bloqueId)).isOne();
    }

    @Test
    void cancelarAntesDeLaFechaLiberaElBloqueYPermiteVolverAReservar() {
        prepararBloque(true);
        var primera = citaService.reservar("patient-test-1", request(null));

        var cancelada = citaService.cancelar("patient-test-1", primera.id());

        assertThat(cancelada.status().name()).isEqualTo("CANCELADA");
        assertThat(jdbc.queryForObject("SELECT disponible FROM bloques_disponibilidad WHERE id = ?", Boolean.class, bloqueId)).isTrue();
        assertThat(catalogoService.obtenerDisponibilidad(medicoId, inicio.toLocalDate(), inicio.toLocalDate()))
                .anyMatch(slot -> slot.id().equals(bloqueId));

        var segunda = citaService.reservar("patient-test-2", request(null));

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
            assertThatThrownBy(() -> citaService.reservar("patient-test-1", request("FORCE_ROLLBACK")))
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
        assertThatThrownBy(() -> citaService.reservar("patient-test-1", null))
                .isInstanceOf(ApiException.class).hasMessage("Los datos de la cita son obligatorios");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM citas WHERE bloque_id = ?", Integer.class, bloqueId)).isZero();
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
        especialidadId = UUID.randomUUID().toString();
        medicoId = UUID.randomUUID().toString();
        bloqueId = UUID.randomUUID().toString();
        inicio = OffsetDateTime.now(ZoneOffset.UTC).plusDays(20).withNano(0);
        jdbc.update("INSERT INTO especialidades(id, nombre, descripcion) VALUES (?, ?, ?)",
                especialidadId, "Especialidad " + especialidadId, "Dato ficticio de prueba");
        jdbc.update("INSERT INTO medicos(id, nombre_completo, especialidad_id, numero_colegiado) VALUES (?, ?, ?, ?)",
                medicoId, "Profesional ficticio", especialidadId, "TEST-" + medicoId);
        jdbc.update("INSERT INTO bloques_disponibilidad(id, medico_id, inicio, fin, disponible) VALUES (?, ?, ?, ?, ?)",
                bloqueId, medicoId, inicio, inicio.plusMinutes(30), disponible);
    }
}
