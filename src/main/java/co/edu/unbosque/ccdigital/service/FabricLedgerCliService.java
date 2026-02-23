package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.dto.FabricDocView;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Servicio que consulta documentos directamente desde Hyperledger Fabric invocando un script CLI (Node.js).
 *
 * <p>Características:</p>
 * <ul>
 *   <li>No consulta base de datos para listar documentos; la fuente es el script {@code list-docs.js}.</li>
 *   <li>Ejecuta el script mediante {@link ExternalToolsService} y parsea el JSON de salida.</li>
 *   <li>Convierte el resultado a {@link FabricDocView} para uso en MVC/Thymeleaf.</li>
 * </ul>
 *
 * <p>Configuración requerida:</p>
 * <ul>
 *   <li>{@code external-tools.fabric.workdir}</li>
 *   <li>{@code external-tools.fabric.node-bin} (por defecto {@code node})</li>
 *   <li>{@code external-tools.fabric.list-docs-script} (por defecto {@code list-docs.js})</li>
 * </ul>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
@Service
public class FabricLedgerCliService {

    private final ExternalToolsService externalToolsService;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Directorio de trabajo en donde se ejecuta el script.
     */
    @Value("${external-tools.fabric.workdir:}")
    private String fabricWorkdir;

    /**
     * Binario Node (por defecto {@code node}).
     */
    @Value("${external-tools.fabric.node-bin:node}")
    private String nodeBin;

    /**
     * Ruta/archivo del script {@code list-docs.js}.
     *
     * <p>Puede ser relativo al workdir (recomendado) o absoluto.</p>
     */
    @Value("${external-tools.fabric.list-docs-script:list-docs.js}")
    private String listDocsScript;

    /**
     * Constructor con inyección de dependencias.
     *
     * @param externalToolsService servicio genérico de ejecución de comandos externos
     */
    public FabricLedgerCliService(ExternalToolsService externalToolsService) {
        this.externalToolsService = externalToolsService;
    }

    /**
     * Ejecuta el script {@code list-docs.js} y devuelve el stdout crudo.
     *
     * <p>El comando ejecutado tiene la forma:</p>
     * <pre>
     * node list-docs.js &lt;idType&gt; &lt;idNumber&gt;
     * </pre>
     *
     * @param idType tipo de identificación (ej. {@code CC}, {@code TI})
     * @param idNumber número de identificación
     * @return salida estándar (stdout) del script
     * @throws RuntimeException si el comando retorna exitCode diferente de 0
     */
    public String listDocsRaw(String idType, String idNumber) {
        List<String> cmd = List.of(nodeBin, listDocsScript, idType, idNumber);

        ExternalToolsService.ExecResult res =
                externalToolsService.exec(cmd, fabricWorkdir, Map.of());

        if (res.getExitCode() != 0) {
            throw new RuntimeException("Fabric listDocs falló (exit=" + res.getExitCode() + "): " + res.getStderr());
        }
        return res.getStdout();
    }

    /**
     * Ejecuta {@link #listDocsRaw(String, String)} y devuelve una lista tipada de {@link FabricDocView}.
     *
     * <p>Este método extrae el primer arreglo JSON encontrado en el stdout (desde el primer {@code '['}
     * hasta el último {@code ']'}). Luego mapea las propiedades esperadas:</p>
     * <ul>
     *   <li>{@code docId}</li>
     *   <li>{@code title}</li>
     *   <li>{@code filePath}</li>
     *   <li>{@code sizeBytes}</li>
     *   <li>{@code createdAt}</li>
     * </ul>
     *
     * <p>El campo {@code status} se deja como {@code null} para que la vista o el DTO aplique
     * valores por defecto. {@code issuingEntity} se toma de la respuesta de Fabric si está disponible.</p>
     *
     * @param idType tipo de identificación
     * @param idNumber número de identificación
     * @return lista de documentos obtenidos desde Fabric
     * @throws RuntimeException si no se puede parsear el JSON o si falla el comando
     */
    public List<FabricDocView> listDocsView(String idType, String idNumber) {
        try {
            String stdout = listDocsRaw(idType, idNumber);
            String json = extractJsonArray(stdout);

            List<Map<String, Object>> items = mapper.readValue(
                    json, new TypeReference<List<Map<String, Object>>>() {}
            );

            return items.stream().map(m -> {
                String docId = asString(m.get("docId"));
                String title = asString(m.get("title"));
                String issuingEntity = asString(m.get("issuingEntity"));
                String filePath = asString(m.get("filePath"));
                Long sizeBytes = asLong(m.get("sizeBytes"));
                String createdAt = asString(m.get("createdAt"));

                return new FabricDocView(
                        docId,
                        title,
                        issuingEntity,
                        null,
                        createdAt,
                        sizeBytes,
                        filePath
                );
            }).toList();

        } catch (Exception e) {
            throw new RuntimeException("No se pudo parsear listDocs desde Fabric", e);
        }
    }

    /**
     * Busca un documento específico por {@code docId} consultando Fabric para el usuario indicado.
     *
     * @param idType tipo de identificación
     * @param idNumber número de identificación
     * @param docId identificador del documento en Fabric
     * @return {@link Optional} con el documento si existe; vacío si no existe
     */
    public Optional<FabricDocView> findDocById(String idType, String idNumber, String docId) {
        return listDocsView(idType, idNumber)
                .stream()
                .filter(d -> d.docId() != null && d.docId().equals(docId))
                .findFirst();
    }

    /**
     * Extrae un arreglo JSON desde una salida de consola que puede incluir logs adicionales.
     *
     * @param stdout salida estándar completa del script
     * @return JSON de tipo arreglo o {@code "[]"} si no se encuentra un arreglo válido
     */
    private static String extractJsonArray(String stdout) {
        if (stdout == null) return "[]";
        int start = stdout.indexOf('[');
        int end = stdout.lastIndexOf(']');
        if (start < 0 || end < 0 || end <= start) return "[]";
        return stdout.substring(start, end + 1).trim();
    }

    /**
     * Convierte un objeto a String de forma segura.
     *
     * @param o valor de entrada
     * @return representación String o {@code null} si {@code o} es null
     */
    private static String asString(Object o) {
        return (o == null) ? null : String.valueOf(o);
    }

    /**
     * Convierte un objeto a {@link Long} de forma segura.
     *
     * @param o valor de entrada
     * @return {@link Long} o {@code null} si no se puede convertir
     */
    private static Long asLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return null; }
    }
}
