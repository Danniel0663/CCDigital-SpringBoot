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

/**
 * Servicio para almacenamiento y recuperación de archivos en el sistema de archivos.
 *
 * <p>La ubicación base se define mediante {@link FileStorageProperties}.</p>
 */
@Service
public class FileStorageService {

    private final FileStorageProperties properties;

    /**
     * Crea el servicio.
     *
     * @param properties propiedades de almacenamiento
     */
    public FileStorageService(FileStorageProperties properties) {
        this.properties = properties;
    }

    /**
     * Obtiene la ruta base configurada para almacenamiento, en forma absoluta y normalizada.
     *
     * @return ruta base
     */
    private Path getBasePath() {
        return Paths.get(properties.getBasePath())
                .toAbsolutePath()
                .normalize();
    }

    /**
     * Asegura la existencia de la carpeta asociada a una persona.
     *
     * <p>El nombre de la carpeta se construye a partir de apellidos y nombres sin espacios,
     * sin acentos y restringido a caracteres alfanuméricos.</p>
     *
     * @param person persona
     * @return ruta de la carpeta de la persona
     * @throws IllegalStateException si no se puede crear el directorio
     */
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

    /**
     * Construye el nombre de carpeta para una persona.
     *
     * @param person persona
     * @return nombre de carpeta normalizado
     */
    private String buildPersonFolderName(Person person) {
        String last = person.getLastName() != null ? person.getLastName() : "";
        String first = person.getFirstName() != null ? person.getFirstName() : "";
        String joined = (last + first).replaceAll("\\s+", "");

        joined = Normalizer.normalize(joined, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .replaceAll("[^A-Za-z0-9]", "");

        return joined;
    }

    /**
     * Construye la ruta relativa (desde basePath) para un archivo asociado a una persona.
     *
     * <p>El nombre del archivo se normaliza usando {@code getFileName()} para reducir
     * riesgos de path traversal.</p>
     *
     * @param person persona propietaria
     * @param filename nombre del archivo
     * @return ruta relativa con separador {@code /}
     */
    public String buildRelativePath(Person person, String filename) {
        Path base = getBasePath();
        Path personFolder = ensurePersonFolder(person);

        String safeName = Paths.get(filename).getFileName().toString();

        Path target = personFolder.resolve(safeName);
        Path rel = base.relativize(target);
        return rel.toString().replace(File.separatorChar, '/');
    }

    /**
     * Almacena un archivo en la carpeta de la persona, calculando tamaño y hash SHA-256.
     *
     * @param person persona propietaria
     * @param file archivo recibido por multipart
     * @return información del archivo almacenado
     * @throws IllegalStateException si ocurre un error de E/S o si el destino queda fuera de basePath
     */
    public StoredFileInfo storePersonFile(Person person, MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            originalName = "upload-" + System.currentTimeMillis();
        }

        originalName = Paths.get(originalName).getFileName().toString();

        try {
            Path base = getBasePath();
            Path personFolder = ensurePersonFolder(person);
            Path target = personFolder.resolve(originalName).normalize();

            if (!target.toAbsolutePath().startsWith(base)) {
                throw new IllegalStateException("Ruta inválida de almacenamiento (fuera de basePath).");
            }

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

    /**
     * Calcula el hash SHA-256 de un archivo.
     *
     * @param file ruta del archivo
     * @return hash SHA-256 en hexadecimal
     * @throws IllegalStateException si ocurre un error de lectura o no existe el algoritmo
     */
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

    /**
     * Resuelve una ruta absoluta a partir del {@code storagePath} almacenado en {@link FileRecord}.
     *
     * <p>Convierte separadores Windows a formato estándar y valida que la ruta resultante
     * permanezca dentro de {@code basePath}.</p>
     *
     * @param fileRecord registro del archivo
     * @return ruta absoluta validada
     * @throws IllegalStateException si el {@code storagePath} es vacío o sale de basePath
     */
    public Path resolvePath(FileRecord fileRecord) {
        String relative = fileRecord.getStoragePath();
        if (relative == null || relative.isBlank()) {
            throw new IllegalStateException("storagePath vacío para FileRecord id=" + fileRecord.getId());
        }

        relative = relative.replace("\\", "/");

        Path base = getBasePath();
        Path resolved = base.resolve(relative).normalize().toAbsolutePath();

        if (!resolved.startsWith(base)) {
            throw new IllegalStateException("Ruta inválida (fuera de basePath).");
        }

        return resolved;
    }

    /**
     * Carga un archivo como {@link Resource} para descarga o visualización.
     *
     * @param fileRecord registro del archivo
     * @return recurso del sistema de archivos
     */
    public Resource loadAsResource(FileRecord fileRecord) {
        Path path = resolvePath(fileRecord);
        return new FileSystemResource(path);
    }

    /**
     * DTO con información del archivo almacenado.
     */
    public static class StoredFileInfo {
        private final String relativePath;
        private final long size;
        private final String sha256;
        private final String originalName;

        /**
         * Construye información del archivo almacenado.
         *
         * @param relativePath ruta relativa desde basePath
         * @param size tamaño en bytes
         * @param sha256 hash SHA-256 en hexadecimal
         * @param originalName nombre original del archivo
         */
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
