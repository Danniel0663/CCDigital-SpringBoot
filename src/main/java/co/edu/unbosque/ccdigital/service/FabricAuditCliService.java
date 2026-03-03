package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.dto.FabricAuditEventView;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Servicio CLI para registrar y consultar eventos de auditoría de Fabric.
 *
 * <p>Opera invocando scripts Node.js externos para no acoplar esta capa Java al SDK de Fabric:</p>
 * <ul>
 *   <li>{@code record-access-event.js}</li>
 *   <li>{@code list-access-events.js}</li>
 * </ul>
 */
@Service
public class FabricAuditCliService {

    // Ejecuta scripts externos de Fabric (record/list).
    private final ExternalToolsService externalToolsService;
    // Parser JSON para transformar stdout de scripts en DTOs tipados.
    private final ObjectMapper mapper;

    // Directorio de ejecución de scripts Node del cliente Fabric.
    @Value("${external-tools.fabric.workdir:}")
    private String fabricWorkdir;

    // Binario de Node.js configurado para entorno local/servidor.
    @Value("${external-tools.fabric.node-bin:node}")
    private String nodeBin;

    // Script que registra transacciones de auditoría on-chain.
    @Value("${external-tools.fabric.record-access-script:record-access-event.js}")
    private String recordAccessScript;

    // Script que consulta transacciones de auditoría on-chain.
    @Value("${external-tools.fabric.list-access-script:list-access-events.js}")
    private String listAccessScript;

    public FabricAuditCliService(ExternalToolsService externalToolsService, ObjectMapper mapper) {
        this.externalToolsService = externalToolsService;
        this.mapper = mapper;
    }

    /**
     * Comando de auditoría para registrar un evento de acceso/verificación.
     */
    public record AuditCommand(
            String idType,
            String idNumber,
            String eventType,
            Long requestId,
            Long personDocumentId,
            String docId,
            String documentTitle,
            Long issuerEntityId,
            String issuerName,
            String action,
            String result,
            String reason,
            String actorType,
            String actorId,
            String source
    ) {
    }

    /**
     * Registra un evento en Fabric (transacción de escritura).
     *
     * @param command datos del evento
     * @return evento retornado por chaincode
     */
    public FabricAuditEventView recordEvent(AuditCommand command) {
        validateFabricCliConfig();

        List<String> cmd = List.of(
                nodeBin,
                recordAccessScript,
                arg(command.idType()),
                arg(command.idNumber()),
                arg(command.eventType()),
                arg(command.requestId()),
                arg(command.personDocumentId()),
                arg(command.docId()),
                arg(command.documentTitle()),
                arg(command.issuerEntityId()),
                arg(command.issuerName()),
                arg(command.action()),
                arg(command.result()),
                arg(command.reason()),
                arg(command.actorType()),
                arg(command.actorId()),
                arg(command.source())
        );

        ExternalToolsService.ExecResult res = externalToolsService.exec(cmd, fabricWorkdir, Map.of());
        if (!res.isOk()) {
            throw new IllegalArgumentException(
                    "No fue posible registrar auditoría en Fabric: " + firstNonBlankLine(res.getStderr(), "Error de ejecución")
            );
        }

        String json = extractJsonObject(res.getStdout());
        try {
            Map<String, Object> payload = mapper.readValue(json, new TypeReference<>() {});
            return toView(payload);
        } catch (Exception ex) {
            throw new IllegalArgumentException("No se pudo parsear la respuesta de auditoría Fabric.", ex);
        }
    }

    /**
     * Lista eventos de auditoría para una persona específica.
     */
    public List<FabricAuditEventView> listEventsForPerson(String idType, String idNumber) {
        validateFabricCliConfig();

        List<String> cmd = List.of(
                nodeBin,
                listAccessScript,
                "--person",
                arg(idType),
                arg(idNumber)
        );

        ExternalToolsService.ExecResult res = externalToolsService.exec(cmd, fabricWorkdir, Map.of());
        if (!res.isOk()) {
            throw new IllegalArgumentException(
                    "No fue posible listar auditoría Fabric: " + firstNonBlankLine(res.getStderr(), "Error de ejecución")
            );
        }

        return parseListPayload(res.getStdout());
    }

    /**
     * Lista global de eventos de auditoría registrados en Fabric.
     */
    public List<FabricAuditEventView> listAllEvents() {
        validateFabricCliConfig();

        List<String> cmd = List.of(nodeBin, listAccessScript, "--all");
        ExternalToolsService.ExecResult res = externalToolsService.exec(cmd, fabricWorkdir, Map.of());
        if (!res.isOk()) {
            throw new IllegalArgumentException(
                    "No fue posible listar auditoría Fabric global: " + firstNonBlankLine(res.getStderr(), "Error de ejecución")
            );
        }

        return parseListPayload(res.getStdout());
    }

    /**
     * Parsea la salida de listado y la transforma en vistas tipadas.
     */
    private List<FabricAuditEventView> parseListPayload(String stdout) {
        String json = extractJsonArray(stdout);
        try {
            List<Map<String, Object>> rows = mapper.readValue(json, new TypeReference<>() {});
            return rows.stream().map(this::toView).toList();
        } catch (Exception ex) {
            throw new IllegalArgumentException("No se pudo parsear listado de auditoría Fabric.", ex);
        }
    }

    /**
     * Mapea un registro JSON genérico a {@link FabricAuditEventView}.
     */
    private FabricAuditEventView toView(Map<String, Object> m) {
        return new FabricAuditEventView(
                asString(m.get("txId")),
                asString(m.get("idType")),
                asString(m.get("idNumber")),
                asString(m.get("eventType")),
                asString(m.get("requestId")),
                asString(m.get("personDocumentId")),
                asString(m.get("docId")),
                asString(m.get("documentTitle")),
                asString(m.get("issuerEntityId")),
                asString(m.get("issuerName")),
                asString(m.get("action")),
                asString(m.get("result")),
                asString(m.get("reason")),
                asString(m.get("actorType")),
                asString(m.get("actorId")),
                asString(m.get("source")),
                asString(m.get("createdAt"))
        );
    }

    /**
     * Verifica configuración mínima para invocación CLI de auditoría Fabric.
     */
    private void validateFabricCliConfig() {
        if (isBlank(fabricWorkdir)) {
            throw new IllegalArgumentException("Falta configurar external-tools.fabric.workdir");
        }
        if (isBlank(nodeBin)) {
            throw new IllegalArgumentException("Falta configurar external-tools.fabric.node-bin");
        }
        if (isBlank(recordAccessScript)) {
            throw new IllegalArgumentException("Falta configurar external-tools.fabric.record-access-script");
        }
        if (isBlank(listAccessScript)) {
            throw new IllegalArgumentException("Falta configurar external-tools.fabric.list-access-script");
        }
    }

    /**
     * Extrae un objeto JSON desde stdout aunque existan logs adicionales.
     */
    private String extractJsonObject(String stdout) {
        if (stdout == null || stdout.isBlank()) {
            throw new IllegalArgumentException("El script de auditoría no devolvió salida.");
        }
        int start = stdout.indexOf('{');
        int end = stdout.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("El script de auditoría no devolvió JSON válido.");
        }
        return stdout.substring(start, end + 1).trim();
    }

    /**
     * Extrae un arreglo JSON desde stdout aunque existan logs adicionales.
     */
    private String extractJsonArray(String stdout) {
        if (stdout == null || stdout.isBlank()) {
            return "[]";
        }
        int start = stdout.indexOf('[');
        int end = stdout.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return "[]";
        }
        return stdout.substring(start, end + 1).trim();
    }

    /**
     * Retorna la primera línea útil de stderr para mensajes de negocio.
     */
    private String firstNonBlankLine(String text, String fallback) {
        if (text == null || text.isBlank()) return fallback;
        return text.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse(fallback);
    }

    /**
     * Normaliza argumento y aplica marcador '-' para valores vacíos.
     */
    private String arg(Object value) {
        String normalized = asString(value).trim();
        return normalized.isBlank() ? "-" : normalized;
    }

    /**
     * Conversión segura a String para payloads dinámicos.
     */
    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * Evalúa si una cadena está vacía o en blanco.
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
