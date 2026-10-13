package com.clinicaserena.catalogo.service;

import com.clinicaserena.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

@Service
public class PractitionerPhotoStorage {
    public record StoredPhoto(String key, String contentType) {}
    private final Path root;
    private final long maxBytes;

    public PractitionerPhotoStorage(
            @Value("${clinica.media.practitioner-photo-root}") String root,
            @Value("${clinica.media.practitioner-photo-max-bytes:2097152}") long maxBytes) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    public StoredPhoto store(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank() || original.length() > 180 || original.contains("/") || original.contains("\\") || original.indexOf('\0') >= 0)
            throw invalid("PHOTO_NAME_INVALID", "El nombre del archivo no es válido");
        if (file.isEmpty())
            throw invalid("PHOTO_SIZE_INVALID", "La foto debe pesar como máximo " + maxBytes + " bytes");
        if (file.getSize() > maxBytes)
            throw tooLarge("La foto debe pesar como máximo " + maxBytes + " bytes");
        try {
            byte[] bytes = file.getBytes();
            String type = detect(bytes);
            BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() < 64 || image.getHeight() < 64 || (long) image.getWidth() * image.getHeight() > 25_000_000L)
                throw invalid("PHOTO_CONTENT_INVALID", "La imagen no es válida");
            Files.createDirectories(root);
            String extension = type.equals("image/png") ? ".png" : ".jpg";
            String key = UUID.randomUUID().toString().toLowerCase(Locale.ROOT) + extension;
            Path target = safePath(key);
            Path partial = safePath(key + ".part");
            try {
                if (!ImageIO.write(image, type.equals("image/png") ? "png" : "jpg", partial.toFile())) throw new IOException("encoder unavailable");
                if (Files.size(partial) > maxBytes) throw invalid("PHOTO_SIZE_INVALID", "La foto procesada excede el tamaño máximo");
                Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
            } finally { Files.deleteIfExists(partial); }
            return new StoredPhoto(key, type);
        } catch (ApiException exception) { throw exception; }
        catch (IOException exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "PHOTO_CONTENT_INVALID", "No se pudo procesar la imagen"); }
    }

    public Resource load(String key) {
        try {
            Path file = safePath(key);
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) throw new ApiException(HttpStatus.NOT_FOUND, "PHOTO_NOT_FOUND", "La foto no está disponible");
            return resource;
        } catch (java.net.MalformedURLException exception) { throw new ApiException(HttpStatus.NOT_FOUND, "PHOTO_NOT_FOUND", "La foto no está disponible"); }
    }

    public void delete(String key) { if (key == null) return; try { Files.deleteIfExists(safePath(key)); } catch (IOException ignored) {} }
    private Path safePath(String key) {
        if (!key.matches("[a-f0-9-]{36}\\.(jpg|png)(\\.part)?")) throw invalid("PHOTO_KEY_INVALID", "Referencia de imagen inválida");
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) throw invalid("PHOTO_KEY_INVALID", "Referencia de imagen inválida");
        return resolved;
    }
    private static String detect(byte[] b) {
        if (b.length >= 8 && b[0] == (byte)0x89 && b[1] == 0x50 && b[2] == 0x4e && b[3] == 0x47 && b[4] == 0x0d && b[5] == 0x0a && b[6] == 0x1a && b[7] == 0x0a) return "image/png";
        if (b.length >= 3 && b[0] == (byte)0xff && b[1] == (byte)0xd8 && b[2] == (byte)0xff) return "image/jpeg";
        throw invalid("PHOTO_TYPE_INVALID", "Solo se permiten imágenes JPEG o PNG");
    }
    private static ApiException invalid(String code, String message) { return new ApiException(HttpStatus.BAD_REQUEST, code, message); }
    private static ApiException tooLarge(String message) { return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "PHOTO_TOO_LARGE", message); }
}
