package com.clinicaserena.catalogo.service;

import com.clinicaserena.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PractitionerPhotoStorageTest {
    @TempDir Path directory;

    @Test void recodificaImagenValidaYNoConservaElNombreOriginal() throws Exception {
        var storage = new PractitionerPhotoStorage(directory.toString(), 2_097_152);
        var stored = storage.store(new MockMultipartFile("photo", "autorizada.png", "image/png", png(120, 120)));
        assertEquals("image/png", stored.contentType());
        assertTrue(stored.key().matches("[a-f0-9-]{36}\\.png"));
        assertTrue(storage.load(stored.key()).exists());
    }

    @Test void rechazaTipoFalsoNombreManipuladoYTamanoExcedido() {
        var storage = new PractitionerPhotoStorage(directory.toString(), 64);
        assertThrows(ApiException.class, () -> storage.store(new MockMultipartFile("photo", "foto.jpg", "image/jpeg", "MZ ejecutable".getBytes())));
        assertThrows(ApiException.class, () -> storage.store(new MockMultipartFile("photo", "../foto.png", "image/png", new byte[20])));
        assertThrows(ApiException.class, () -> storage.store(new MockMultipartFile("photo", "foto.png", "image/png", new byte[65])));
        assertDoesNotThrow(() -> assertEquals(0, Files.list(directory).count()));
    }

    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics(); graphics.setColor(Color.CYAN); graphics.fillRect(0, 0, width, height); graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream(); ImageIO.write(image, "png", output); return output.toByteArray();
    }
}
