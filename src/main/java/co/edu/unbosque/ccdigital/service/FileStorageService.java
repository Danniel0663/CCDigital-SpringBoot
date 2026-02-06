package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.config.FileStorageProperties;
import co.edu.unbosque.ccdigital.entity.FileRecord;
import co.edu.unbosque.ccdigital.entity.Person;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;

@Service
public class FileStorageService {

    private final FileStorageProperties properties;

    public FileStorageService(FileStorageProperties properties) {
        this.properties = properties;
    }

    private Path getBasePath() {
        return Paths.get(properties.getBasePath())
                .toAbsolutePath()
                .normalize();
    }

    /** Crea (si no existe) la carpeta de la persona: ApellidosNombres */
    public Path ensurePersonFolder(Person person) {
        try {
            String folderName = buildPersonFolderName(person);
            Path personFolder = getBasePath().resolve(folderName);
            Files.createDirectories(personFolder);
            return personFolder;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear la carpeta de la persona", e);
        }
    }

    private String buildPersonFolderName(Person person) {
        String last = person.getLastName() != null ? person.getLastName() : "";
        String first = person.getFirstName() != null ? person.getFirstName() : "";
        String joined = (last + first).replaceAll("\\s+", "");

        joined = Normalizer.normalize(joined, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .replaceAll("[^A-Za-z0-9]", "");

        return joined;
    }

    /** Ruta relativa (desde basePath) para un archivo de esa persona. */
    public String buildRelativePath(Person person, String filename) {
        Path base = getBasePath();
        Path personFolder = ensurePersonFolder(person);
        Path target = personFolder.resolve(filename);
        Path rel = base.relativize(target);
        return rel.toString().replace(File.separatorChar, '/');
    }

    /**
     * Copia el archivo al disco en la carpeta de la persona, calcula tamaño y hash,
     * y devuelve esos datos.
     */
    public StoredFileInfo storePersonFile(Person person, MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            originalName = "upload-" + System.currentTimeMillis();
        }

        try {
            Path base = getBasePath();
            Path personFolder = ensurePersonFolder(person);
            Path target = personFolder.resolve(originalName).normalize();

            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            long size = Files.size(target);
            String sha256 = calculateSha256(target);

            Path rel = base.relativize(target);
            String relativePath = rel.toString().replace(File.separatorChar, '/');

            return new StoredFileInfo(relativePath, size, sha256, originalName);
        } catch (IOException e) {
            throw new IllegalStateException("Error guardando archivo en disco", e);
        }
    }

    private String calculateSha256(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                digest.update(buffer, 0, n);
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Error calculando hash SHA-256", e);
        }
    }

    // ==== descarga ====

    public Path resolvePath(FileRecord fileRecord) {
        String relative = fileRecord.getStoragePath();
        if (relative == null || relative.isBlank()) {
            throw new IllegalStateException("storagePath vacío para FileRecord id=" + fileRecord.getId());
        }

        // ✅ Corrección mínima: aceptar rutas guardadas con "\" (Windows) y "/" (Linux)
        relative = relative.replace("\\", "/");

        return getBasePath()
                .resolve(relative)
                .normalize()
                .toAbsolutePath();
    }

    public Resource loadAsResource(FileRecord fileRecord) {
        Path path = resolvePath(fileRecord);
        return new FileSystemResource(path);
    }

    public static class StoredFileInfo {
        private final String relativePath;
        private final long size;
        private final String sha256;
        private final String originalName;

        public StoredFileInfo(String relativePath, long size, String sha256, String originalName) {
            this.relativePath = relativePath;
            this.size = size;
            this.sha256 = sha256;
            this.originalName = originalName;
        }

        public String getRelativePath() { return relativePath; }
        public long getSize() { return size; }
        public String getSha256() { return sha256; }
        public String getOriginalName() { return originalName; }
    }
}
