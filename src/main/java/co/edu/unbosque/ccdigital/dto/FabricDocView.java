package co.edu.unbosque.ccdigital.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Proyección de un documento consultado en Hyperledger Fabric para visualización en la UI.
 *
 * <p>
 * Esta vista se construye con base en la salida del proceso de consulta (por ejemplo, un script CLI)
 * y se utiliza para presentar información relevante del documento (título, entidad, estado, tamaño y ruta).
 * </p>
 *
 * <h2>Convenciones</h2>
 * <ul>
 *   <li>{@code createdAt} debe venir en formato ISO-8601 compatible con {@link Instant#parse(CharSequence)}.</li>
 *   <li>{@code issuingEntity} y {@code status} se normalizan con valores por defecto si llegan vacíos.</li>
 * </ul>
 *
 * @param docId identificador del documento en Fabric
 * @param title título del documento
 * @param issuingEntity entidad emisora o etiqueta de origen
 * @param status estado del documento para presentación
 * @param createdAt fecha/hora de creación (ISO-8601)
 * @param sizeBytes tamaño del archivo en bytes
 * @param filePath ruta del archivo asociada al documento
 *
 * @since 3.0
 */
public record FabricDocView(
        String docId,
        String title,
        String issuingEntity,
        String status,
        String createdAt,
        Long sizeBytes,
        String filePath
) {
    /**
     * Zona horaria de presentación para timestamps provenientes de Fabric.
     *
     * <p>Se fija explícitamente en Colombia para evitar desfases cuando el JVM se ejecuta con otra
     * zona horaria (por ejemplo, UTC en el IDE o en despliegues).</p>
     */
    private static final ZoneId UI_ZONE = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter UI_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(UI_ZONE);


    /**
     * Constructor compacto del record.
     *
     * <p>
     * Normaliza valores de presentación para evitar nulos o cadenas vacías en UI.
     * </p>
     */
    public FabricDocView {
        issuingEntity = (issuingEntity == null || issuingEntity.isBlank()) ? "Fabric" : issuingEntity;
        status = (status == null || status.isBlank()) ? "Registrado" : status;
    }

    /**
     * Retorna la fecha de creación en un formato legible para la UI.
     *
     * @return fecha/hora formateada o "No disponible" si no existe
     */
    public String createdAtHuman() {
        if (createdAt == null || createdAt.isBlank()) return "No disponible";
        try {
            Instant instant = parseCreatedAtToInstant(createdAt);
            if (instant == null) {
                return createdAt;
            }
            return UI_DATE_TIME.format(instant);
        } catch (Exception e) {
            return createdAt;
        }
    }

    /**
     * Interpreta timestamps de Fabric con y sin zona horaria.
     *
     * <p>Cuando llega un valor sin offset (ej. {@code 2026-03-02T18:34:56}), se asume UTC
     * para mantener consistencia con las marcas temporales on-chain y evitar desfases en UI.</p>
     */
    private static Instant parseCreatedAtToInstant(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String raw = rawValue.trim();

        try {
            return OffsetDateTime.parse(raw).toInstant();
        } catch (DateTimeParseException ignored) {
            // Puede llegar en formato Instant puro.
        }

        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException ignored) {
            // Puede llegar sin zona horaria.
        }

        try {
            String normalized = raw.replace(' ', 'T');
            LocalDateTime utcDateTime = LocalDateTime.parse(normalized);
            return utcDateTime.atOffset(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    /**
     * Retorna el tamaño del archivo en una unidad legible (B, KB, MB, GB).
     *
     * @return tamaño formateado o "No disponible" si no existe
     */
    public String sizeHuman() {
        if (sizeBytes == null || sizeBytes <= 0) return "No disponible";
        BigDecimal b = BigDecimal.valueOf(sizeBytes);

        BigDecimal kb = BigDecimal.valueOf(1024);
        BigDecimal mb = kb.multiply(kb);
        BigDecimal gb = mb.multiply(kb);

        if (b.compareTo(gb) >= 0) return b.divide(gb, 2, RoundingMode.HALF_UP) + " GB";
        if (b.compareTo(mb) >= 0) return b.divide(mb, 2, RoundingMode.HALF_UP) + " MB";
        if (b.compareTo(kb) >= 0) return b.divide(kb, 2, RoundingMode.HALF_UP) + " KB";
        return b + " B";
    }

    /**
     * Retorna el nombre del archivo extraído desde {@link #filePath()}.
     *
     * @return nombre de archivo o un valor por defecto si no puede resolverse
     */
    public String fileName() {
        if (filePath == null || filePath.isBlank()) return "documento";
        try {
            Path p = Paths.get(filePath);
            return (p.getFileName() != null) ? p.getFileName().toString() : "documento";
        } catch (Exception e) {
            return "documento";
        }
    }
}
