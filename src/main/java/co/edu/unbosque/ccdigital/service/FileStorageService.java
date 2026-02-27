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
import java.util.Objects;

/**
 * Servicio encargado del almacenamiento y recuperación de archivos en el sistema de archivos local.
 *
 * <p>Este servicio utiliza la propiedad {@code ccdigital.fs.basePath} (inyectada vía
 * {@link FileStorageProperties}) como directorio raíz para guardar archivos.</p>
 *
 * <p>Comportamiento principal:</p>
 * <ul>
 *   <li>Guarda archivos dentro de una carpeta por persona (nombre derivado de apellidos+nombres).</li>
 *   <li>Calcula metadatos del archivo almacenado:
 *     <ul>
 *       <li>Tamaño en bytes.</li>
 *       <li>Hash SHA-256.</li>
 *       <li>Ruta relativa desde el {@code basePath} (normalizada con {@code /}).</li>
 *     </ul>
 *   </li>
 *   <li>Resuelve rutas absolutas a partir de rutas relativas persistidas en {@link FileRecord}.</li>
 * </ul>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
@Service
public class FileStorageService {

    /**
     * Propiedades de almacenamiento de archivos (basePath).
     */
    private final FileStorageProperties properties;

    /**
     * Crea una instancia del servicio.
     *
     * @param properties propiedades de almacenamiento
     */
    public FileStorageService(FileStorageProperties properties) {
        this.properties = properties;
    }

    /**
     * Obtiene el directorio base configurado, convertido a {@link Path} absoluto y normalizado.
     *
     * @return ruta base absoluta y normalizada
     */
    private Path getBasePath() {
        return Paths.get(properties.getBasePath())
                .toAbsolutePath()
                .normalize();
    }

    /**
     * Crea (si no existe) la carpeta de la persona dentro del {@code basePath}.
     *
     * <p>El nombre de la carpeta se genera con {@link #buildPersonFolderName(Person)}.</p>
     *
     * @param person persona para la cual se asegura el directorio
     * @return ruta de la carpeta de la persona
     * @throws IllegalStateException si no es posible crear el directorio
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
     * Construye el nombre de carpeta para una persona usando apellidos + nombres,
     * removiendo espacios y caracteres no permitidos.
     *
     * <p>Reglas aplicadas:</p>
     * <ul>
     *   <li>Concatena apellido y nombre.</li>
     *   <li>Elimina espacios.</li>
     *   <li>Normaliza y elimina tildes/diacríticos (ASCII).</li>
     *   <li>Deja únicamente caracteres alfanuméricos.</li>
     * </ul>
     *
     * @param person persona fuente
     * @return nombre de carpeta seguro (ASCII alfanumérico)
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
     * Construye la ruta relativa (desde {@code basePath}) para un archivo de una persona.
     *
     * <p>Ejemplo de salida: {@code ApellidoNombre/documento.pdf}</p>
     *
     * <p>La ruta se normaliza a separador {@code /} para ser consistente al persistirse,
     * independientemente del sistema operativo.</p>
     *
     * @param person persona propietaria
     * @param filename nombre del archivo
     * @return ruta relativa en formato con {@code /}
     */
    public String buildRelativePath(Person person, String filename) {
        Path base = getBasePath();
        Path personFolder = ensurePersonFolder(person);
        Path target = personFolder.resolve(filename);
        Path rel = base.relativize(target);
        return rel.toString().replace(File.separatorChar, '/');
    }

    /**
     * Almacena un archivo en disco dentro de la carpeta de la persona, calculando metadatos.
     *
     * <p>Comportamiento:</p>
     * <ul>
     *   <li>Si {@code file.getOriginalFilename()} es vacío, genera un nombre {@code upload-&lt;timestamp&gt;}.</li>
     *   <li>Escribe el archivo con {@link StandardCopyOption#REPLACE_EXISTING}.</li>
     *   <li>Calcula tamaño y hash SHA-256 del archivo ya almacenado.</li>
     *   <li>Retorna metadatos en {@link StoredFileInfo}.</li>
     * </ul>
     *
     * @param person persona propietaria del archivo
     * @param file archivo recibido (multipart)
     * @return información del archivo almacenado
     * @throws IllegalStateException si ocurre un error guardando el archivo
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

    /**
     * Calcula el hash SHA-256 de un archivo en disco.
     *
     * @param file ruta del archivo
     * @return hash SHA-256 en hexadecimal (minúsculas)
     * @throws IllegalStateException si no se puede leer el archivo o no existe el algoritmo
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
     * Resuelve la ruta absoluta del archivo a partir del {@link FileRecord#getStoragePath()}.
     *
     * <p>El {@code storagePath} se espera como ruta relativa a {@code basePath}. Para evitar problemas
     * por rutas guardadas con separadores de Windows ({@code \\}), se normaliza a {@code /} antes de resolver.</p>
     *
     * @param fileRecord registro de archivo con ruta relativa persistida
     * @return ruta absoluta y normalizada del archivo en disco
     * @throws IllegalStateException si el {@code storagePath} está vacío
     */
    public Path resolvePath(FileRecord fileRecord) {
        String relative = fileRecord.getStoragePath();
        if (relative == null || relative.isBlank()) {
            throw new IllegalStateException("storagePath vacío para FileRecord id=" + fileRecord.getId());
        }

        relative = relative.replace("\\", "/");

        return getBasePath()
                .resolve(relative)
                .normalize()
                .toAbsolutePath();
    }

    /**
     * Carga un archivo como {@link Resource} para ser retornado por controladores (descarga/visualización).
     *
     * @param fileRecord registro de archivo
     * @return recurso basado en sistema de archivos
     */
    public Resource loadAsResource(FileRecord fileRecord) {
        Path path = resolvePath(fileRecord);
        return new FileSystemResource(Objects.requireNonNull(path));
    }

    /**
     * DTO simple con información del archivo almacenado en disco.
     *
     * <p>Se utiliza típicamente al momento de persistir un {@link FileRecord} o asociar un archivo
     * a un {@code PersonDocument}.</p>
     */
    public static class StoredFileInfo {

        /**
         * Ruta relativa desde {@code basePath} en formato con {@code /}.
         */
        private final String relativePath;

        /**
         * Tamaño del archivo en bytes.
         */
        private final long size;

        /**
         * Hash SHA-256 del archivo en hexadecimal.
         */
        private final String sha256;

        /**
         * Nombre original del archivo cargado.
         */
        private final String originalName;

        /**
         * Crea una instancia con metadatos del archivo almacenado.
         *
         * @param relativePath ruta relativa desde {@code basePath}
         * @param size tamaño en bytes
         * @param sha256 hash SHA-256 hex
         * @param originalName nombre original del archivo
         */
        public StoredFileInfo(String relativePath, long size, String sha256, String originalName) {
            this.relativePath = relativePath;
            this.size = size;
            this.sha256 = sha256;
            this.originalName = originalName;
        }

        /**
         * @return ruta relativa desde {@code basePath}
         */
        public String getRelativePath() {
            return relativePath;
        }

        /**
         * @return tamaño del archivo en bytes
         */
        public long getSize() {
            return size;
        }

        /**
         * @return hash SHA-256 en hexadecimal
         */
        public String getSha256() {
            return sha256;
        }

        /**
         * @return nombre original del archivo
         */
        public String getOriginalName() {
            return originalName;
        }
    }
}
