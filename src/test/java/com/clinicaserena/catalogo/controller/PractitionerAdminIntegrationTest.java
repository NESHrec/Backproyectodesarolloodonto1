package com.clinicaserena.catalogo.controller;

import com.clinicaserena.catalogo.service.PractitionerAdminService;
import com.clinicaserena.staff.entity.RolPersonal;
import com.clinicaserena.staff.security.StaffPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.MediaType;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PractitionerAdminIntegrationTest {
    private String ADMIN;
    private String DOCTOR;
    private String SPECIALTY;
    private String PRACTITIONER;
    private String BASE_LICENSE;
    private final Set<String> createdPractitionerIds = ConcurrentHashMap.newKeySet();
    private final Set<String> createdLicenses = ConcurrentHashMap.newKeySet();
    private static final String PASSWORD = "PractitionerTest!2026Aa";
    private static final Path PHOTO_ROOT = Path.of(System.getProperty("java.io.tmpdir"), "clinica-serena-photo-integration-" + UUID.randomUUID());

    @DynamicPropertySource
    static void photoStorage(DynamicPropertyRegistry registry) {
        registry.add("clinica.media.practitioner-photo-root", () -> PHOTO_ROOT.toString());
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper mapper;
    @Autowired PractitionerAdminService adminService;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void prepare() {
        ADMIN = UUID.randomUUID().toString();
        DOCTOR = UUID.randomUUID().toString();
        SPECIALTY = UUID.randomUUID().toString();
        PRACTITIONER = UUID.randomUUID().toString();
        BASE_LICENSE = "PHOTO-BASE-" + PRACTITIONER;
        createdPractitionerIds.add(PRACTITIONER);
        createdLicenses.add(BASE_LICENSE);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        String hash = passwordEncoder.encode(PASSWORD);
        jdbc.update("INSERT INTO cuentas_personal(id,email_normalizado,nombre_completo,password_hash,rol,estado,creado_en,actualizada_en) VALUES (?,?,?,?,'ADMIN','ACTIVA',?,?)", ADMIN, "photo.admin." + ADMIN + "@example.test", "ADMIN sintético de pruebas", hash, now, now);
        jdbc.update("INSERT INTO cuentas_personal(id,email_normalizado,nombre_completo,password_hash,rol,estado,creado_en,actualizada_en) VALUES (?,?,?,?,'MEDICO','ACTIVA',?,?)", DOCTOR, "photo.doctor." + DOCTOR + "@example.test", "MEDICO sintético de pruebas", hash, now, now);
        jdbc.update("INSERT INTO especialidades(id,nombre,descripcion) VALUES (?,?,?)", SPECIALTY, "Especialidad de fotos " + SPECIALTY, "Fixture de pruebas");
        jdbc.update("INSERT INTO medicos(id,nombre_completo,especialidad_id,numero_colegiado) VALUES (?,?,?,?)", PRACTITIONER, "Profesional sintético de fotos", SPECIALTY, "PHOTO-BASE-" + PRACTITIONER);
    }

    @AfterEach
    void cleanup() throws Exception {
        cleanFixture();
        if (Files.exists(PHOTO_ROOT)) try (var paths = Files.list(PHOTO_ROOT)) {
            for (Path path : paths.toList()) Files.deleteIfExists(path);
        }
    }

    @AfterAll
    static void removePhotoArtifacts() throws Exception {
        if (Files.exists(PHOTO_ROOT)) try (var paths = Files.walk(PHOTO_ROOT)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    @Test
    void desactivacionBloqueaCuentaMedicoActivaYPermiteDesvinculadaReactivacion() throws Exception {
        String token = loginAdmin();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        jdbc.update("UPDATE cuentas_personal SET medico_id=?,medico_vinculado_en=?,medico_vinculado_por=? WHERE id=?", PRACTITIONER, now, ADMIN, DOCTOR);
        mvc.perform(patch("/api/v1/staff/medicos/{id}/estado", PRACTITIONER).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRACTITIONER_HAS_ACTIVE_ACCOUNT"));
        assertThat(jdbc.queryForObject("SELECT activo FROM medicos WHERE id=?", Boolean.class, PRACTITIONER)).isTrue();
        assertThat(jdbc.queryForObject("SELECT medico_id FROM cuentas_personal WHERE id=?", String.class, DOCTOR)).isEqualTo(PRACTITIONER);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM historial_vinculacion_medico WHERE cuenta_personal_id=?", Integer.class, DOCTOR)).isZero();

        jdbc.update("UPDATE cuentas_personal SET medico_id=NULL,medico_vinculado_en=NULL,medico_vinculado_por=NULL WHERE id=?", DOCTOR);
        mvc.perform(patch("/api/v1/staff/medicos/{id}/estado", PRACTITIONER).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(patch("/api/v1/staff/medicos/{id}/estado", PRACTITIONER).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void eliminaProfesionalInactivoSinReferenciasYRegistraAuditoria() throws Exception {
        String token = loginAdmin();
        mvc.perform(delete("/api/v1/staff/medicos/{id}", PRACTITIONER).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRACTITIONER_MUST_BE_INACTIVE"));

        mvc.perform(patch("/api/v1/staff/medicos/{id}/estado", PRACTITIONER).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/staff/medicos/{id}", PRACTITIONER).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM medicos WHERE id=?", Integer.class, PRACTITIONER)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bitacora_eventos WHERE actor_personal_id=? AND accion=? AND entidad_id=?", Integer.class, ADMIN, "PRACTITIONER_DELETED", PRACTITIONER)).isEqualTo(1);
    }

    @Test
    void eliminaProfesionalInactivoRechazaHistorialConservandoElRegistro() throws Exception {
        String token = loginAdmin();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        jdbc.update("INSERT INTO historial_vinculacion_medico(id,cuenta_personal_id,medico_anterior_id,medico_nuevo_id,realizado_por,realizado_en) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID().toString(), DOCTOR, PRACTITIONER, null, ADMIN, now);
        mvc.perform(patch("/api/v1/staff/medicos/{id}/estado", PRACTITIONER).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/staff/medicos/{id}", PRACTITIONER).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRACTITIONER_HAS_HISTORY"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM medicos WHERE id=?", Integer.class, PRACTITIONER)).isEqualTo(1);
    }

    @Test
    void colegiadoSeNormalizaYRestriccionUnicaResisteSolicitudesConcurrentes() throws Exception {
        String token = loginAdmin();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        String expectedIndividual = "AB " + suffix;
        String first = create(token, "  ab  " + suffix.toLowerCase(Locale.ROOT) + "  ");
        assertThat(jdbc.queryForObject("SELECT numero_colegiado FROM medicos WHERE id=?", String.class, first)).isEqualTo(expectedIndividual);
        String expectedRace = "RACE " + suffix;
        String firstRaceVariant = "  race  " + suffix.toLowerCase(Locale.ROOT) + " ";
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<ConcurrentCreateResult> one = new AtomicReference<>();
        AtomicReference<ConcurrentCreateResult> two = new AtomicReference<>();
        try {
            trackLicense(firstRaceVariant);
            trackLicense(expectedRace);
            var a = pool.submit((java.util.concurrent.Callable<Void>) () -> { concurrentCreate(token, firstRaceVariant, ready, start, one); return null; });
            var b = pool.submit((java.util.concurrent.Callable<Void>) () -> { concurrentCreate(token, expectedRace, ready, start, two); return null; });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            a.get(30, TimeUnit.SECONDS); b.get(30, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertThat(List.of(one.get().status(), two.get().status())).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM medicos WHERE numero_colegiado=?", Integer.class, expectedRace)).isEqualTo(1);
    }

    @Test
    void reemplazoPublicaImagenNuevaYRollbackLimpiaNuevaConservandoAnterior() throws Exception {
        String token = loginAdmin();
        String id = create(token, "PHOTO-RB-" + UUID.randomUUID().toString().replace("-", ""));
        String first = upload(token, id, png(Color.RED));
        String oldKey = jdbc.queryForObject("SELECT foto_clave FROM medicos WHERE id=?", String.class, id);
        assertThat(Files.exists(PHOTO_ROOT.resolve(oldKey))).isTrue();

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        byte[] rolledBackPhoto = png(Color.BLUE);
        transaction.execute(status -> {
            adminService.replacePhoto(new StaffPrincipal(ADMIN, "admin@example.test", "Admin test", RolPersonal.ADMIN, "synthetic"), id,
                    new MockMultipartFile("photo", "second.png", "image/png", rolledBackPhoto));
            status.setRollbackOnly();
            return null;
        });
        assertThat(jdbc.queryForObject("SELECT foto_clave FROM medicos WHERE id=?", String.class, id)).isEqualTo(oldKey);
        assertThat(Files.exists(PHOTO_ROOT.resolve(oldKey))).isTrue();
        try (var files = Files.list(PHOTO_ROOT)) { assertThat(files.count()).isEqualTo(1); }

        upload(token, id, png(Color.GREEN));
        String newKey = jdbc.queryForObject("SELECT foto_clave FROM medicos WHERE id=?", String.class, id);
        assertThat(newKey).isNotEqualTo(oldKey);
        assertThat(Files.exists(PHOTO_ROOT.resolve(oldKey))).isFalse();
        var publicResponse = mvc.perform(get("/api/v1/medicos/{id}/foto", id).param("v", newKey))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse();
        Color actual = new Color(ImageIO.read(new java.io.ByteArrayInputStream(publicResponse.getContentAsByteArray())).getRGB(20, 20));
        assertThat(actual.getGreen()).isGreaterThan(180);

        mvc.perform(patch("/api/v1/staff/medicos/{id}/estado", id).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/medicos/{id}/foto", id)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/staff/medicos/{id}/estado", id).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/staff/medicos/{id}/foto", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.photoUrl").doesNotExist());
        assertThat(Files.exists(PHOTO_ROOT.resolve(newKey))).isFalse();
    }

    private String loginAdmin() throws Exception {
        String email = "photo.admin." + ADMIN + "@example.test";
        String body = mvc.perform(post("/api/v1/staff/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("accessToken").asText();
    }

    private String create(String token, String license) throws Exception {
        trackLicense(license);
        String body = mvc.perform(post("/api/v1/staff/medicos").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Profesional de prueba\",\"specialtyId\":\"" + SPECIALTY + "\",\"licenseNumber\":\"" + license + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(body).get("id").asText();
        createdPractitionerIds.add(id);
        return id;
    }

    private void concurrentCreate(String token, String license, CountDownLatch ready, CountDownLatch start, AtomicReference<ConcurrentCreateResult> result) throws Exception {
        ready.countDown(); start.await();
        String body = "{\"fullName\":\"Profesional concurrente\",\"specialtyId\":\"" + SPECIALTY + "\",\"licenseNumber\":\"" + license + "\"}";
        var response = mvc.perform(post("/api/v1/staff/medicos").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
        String id = null;
        if (response.getStatus() == 201) {
            id = mapper.readTree(response.getContentAsString()).get("id").asText();
            createdPractitionerIds.add(id);
        }
        result.set(new ConcurrentCreateResult(response.getStatus(), id));
    }

    private void trackLicense(String license) {
        createdLicenses.add(license.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT));
    }

    private record ConcurrentCreateResult(int status, String id) {}

    private String upload(String token, String id, byte[] image) throws Exception {
        MockMultipartHttpServletRequestBuilder upload = multipart("/api/v1/staff/medicos/{id}/foto", id).file(new MockMultipartFile("photo", "authorized-test.png", "image/png", image));
        upload.with(request -> { request.setMethod("PUT"); return request; });
        mvc.perform(upload.header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        return jdbc.queryForObject("SELECT foto_clave FROM medicos WHERE id=?", String.class, id);
    }

    private static byte[] png(Color color) throws Exception {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics(); graphics.setColor(color); graphics.fillRect(0, 0, 100, 100); graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream(); ImageIO.write(image, "png", out); return out.toByteArray();
    }

    private void cleanFixture() {
        if (ADMIN == null || DOCTOR == null || SPECIALTY == null) return;
        jdbc.update("DELETE FROM sesiones_personal WHERE cuenta_id IN (?,?)", ADMIN, DOCTOR);
        jdbc.update("DELETE FROM historial_vinculacion_medico WHERE cuenta_personal_id=? OR realizado_por=?", DOCTOR, ADMIN);
        jdbc.update("UPDATE cuentas_personal SET medico_id=NULL,medico_vinculado_en=NULL,medico_vinculado_por=NULL WHERE id=?", DOCTOR);
        jdbc.update("DELETE FROM cuentas_personal WHERE id IN (?,?)", ADMIN, DOCTOR);
        if (!createdLicenses.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(createdLicenses.size(), "?"));
            jdbc.update("DELETE FROM medicos WHERE numero_colegiado IN (" + placeholders + ")", createdLicenses.toArray());
        }
        if (!createdPractitionerIds.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(createdPractitionerIds.size(), "?"));
            jdbc.update("DELETE FROM medicos WHERE id IN (" + placeholders + ")", createdPractitionerIds.toArray());
        }
        jdbc.update("DELETE FROM especialidades WHERE id=?", SPECIALTY);
    }
}
