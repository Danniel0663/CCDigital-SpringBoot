package co.edu.unbosque.ccdigital.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

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
            Instant inst = Instant.parse(createdAt);
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.systemDefault());
            return fmt.format(inst);
        } catch (Exception e) {
            return createdAt;
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
