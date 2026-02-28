package co.edu.unbosque.ccdigital.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio para resolver el detalle técnico completo de una referencia blockchain del dashboard Admin.
 *
 * <p>Soporta dos redes:</p>
 * <ul>
 *   <li>Fabric: ejecuta un script Node externo que resuelve docId -> bloque/tx real.</li>
 *   <li>Indy: consulta el record completo de present-proof en ACA-Py usando pres_ex_id.</li>
 * </ul>
 */
@Service
public class BlockchainTraceDetailService {

    // Ejecuta comandos externos definidos en application.properties.
    private final ExternalToolsService externalToolsService;
    // Cliente lógico de ACA-Py para trazas Indy.
    private final IndyProofLoginService indyProofLoginService;
    // Parser/serializador JSON para normalizar respuesta de scripts externos.
    private final ObjectMapper objectMapper;

    // Directorio donde viven scripts Node del cliente Fabric.
    @Value("${external-tools.fabric.workdir:}")
    private String fabricWorkdir;

    // Binario Node configurable (node, nodejs, ruta absoluta, etc.).
    @Value("${external-tools.fabric.node-bin:node}")
    private String nodeBin;

    // Script que resuelve docId -> bloque/tx real en la red Fabric.
    @Value("${external-tools.fabric.block-reader-script:read-block-by-ref.js}")
    private String fabricBlockReaderScript;

    public BlockchainTraceDetailService(ExternalToolsService externalToolsService,
                                        IndyProofLoginService indyProofLoginService,
                                        ObjectMapper objectMapper) {
        this.externalToolsService = externalToolsService;
        this.indyProofLoginService = indyProofLoginService;
        this.objectMapper = objectMapper;
    }

    /**
     * Resuelve el detalle completo del bloque/intercambio para la referencia indicada.
     *
     * @param network red objetivo (Fabric/Indy)
     * @param reference referencia funcional (docId o pres_ex_id)
     * @param idType tipo de identificación (requerido para Fabric)
     * @param idNumber número de identificación (requerido para Fabric)
     * @return payload listo para UI
     */
    public Map<String, Object> readDetail(String network,
                                          String reference,
                                          String idType,
                                          String idNumber) {
        String networkNorm = normalize(network).toLowerCase();
        if (networkNorm.isBlank()) {
            throw new IllegalArgumentException("Debe indicar la red blockchain (Fabric o Indy).");
        }

        return switch (networkNorm) {
            case "fabric" -> readFabricDetail(reference, idType, idNumber);
            case "indy" -> readIndyDetail(reference);
            default -> throw new IllegalArgumentException("Red no soportada para detalle: " + network);
        };
    }

    /**
     * Lee el bloque real de Fabric usando script externo por referencia docId.
     */
    private Map<String, Object> readFabricDetail(String reference, String idType, String idNumber) {
        String ref = normalize(reference);
        String idTypeNorm = normalize(idType).toUpperCase();
        String idNumberNorm = normalize(idNumber);

        if (ref.isBlank()) {
            throw new IllegalArgumentException("La referencia del bloque Fabric es obligatoria.");
        }
        if (idTypeNorm.isBlank() || idNumberNorm.isBlank()) {
            throw new IllegalArgumentException("Para Fabric debe indicar tipo y número de identificación.");
        }
        if (normalize(fabricWorkdir).isBlank()) {
            throw new IllegalArgumentException("No está configurado external-tools.fabric.workdir.");
        }

        List<String> command = List.of(nodeBin, fabricBlockReaderScript, idTypeNorm, idNumberNorm, ref);
        ExternalToolsService.ExecResult result = externalToolsService.exec(command, fabricWorkdir, Map.of());
        if (!result.isOk()) {
            throw new IllegalArgumentException(
                    "No fue posible leer el bloque en Fabric: " + firstNonBlankLine(result.getStderr(), "Error de ejecución")
            );
        }

        String json = extractJsonObject(result.getStdout());
        try {
            Map<String, Object> payload = objectMapper.readValue(
                    json, new TypeReference<Map<String, Object>>() {}
            );
            if (payload == null) {
                throw new IllegalArgumentException("El lector de Fabric no devolvió contenido.");
            }
            return payload;
        } catch (Exception ex) {
            throw new IllegalArgumentException("No se pudo parsear la respuesta del lector Fabric.", ex);
        }
    }

    /**
     * Lee el intercambio completo de Indy/ACA-Py usando pres_ex_id.
     */
    private Map<String, Object> readIndyDetail(String reference) {
        String ref = normalize(reference);
        if (ref.isBlank()) {
            throw new IllegalArgumentException("La referencia de Indy es obligatoria.");
        }

        Map<String, Object> record = indyProofLoginService.getProofRecordDetail(ref);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("network", "Indy");
        payload.put("referenceType", "pres_ex_id");
        payload.put("reference", ref);
        payload.put("operation", "Verificación de credencial");
        payload.put("status", normalizeOrFallback(asString(record.get("state")).toUpperCase(), "UNKNOWN"));
        payload.put("eventAt", normalizeOrFallback(asString(record.get("updated_at")), asString(record.get("created_at"))));
        payload.put("summary", "Detalle completo del intercambio present-proof 2.0 reportado por ACA-Py.");
        payload.put("resolved", Map.of(
                "presExId", ref,
                "verified", record.get("verified")
        ));
        payload.put("raw", record);
        return payload;
    }

    /**
     * Extrae el primer objeto JSON completo desde stdout (incluso si hay logs previos).
     */
    private String extractJsonObject(String stdout) {
        if (stdout == null || stdout.isBlank()) {
            throw new IllegalArgumentException("El lector no retornó salida.");
        }
        int start = stdout.indexOf('{');
        int end = stdout.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("El lector no retornó un JSON válido.");
        }
        return stdout.substring(start, end + 1).trim();
    }

    private String firstNonBlankLine(String text, String fallback) {
        // Devuelve la primera línea útil del stderr para mensajes de error legibles en UI.
        if (text == null || text.isBlank()) return fallback;
        return text.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse(fallback);
    }

    private static String normalize(String value) {
        // Normaliza nulos a vacío para simplificar validaciones.
        return value == null ? "" : value.trim();
    }

    private static String normalizeOrFallback(String value, String fallback) {
        // Aplica fallback cuando el dato recibido viene vacío.
        String normalized = normalize(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String asString(Object value) {
        // Conversión segura para mapas deserializados sin tipos estrictos.
        return value == null ? "" : String.valueOf(value);
    }
}
