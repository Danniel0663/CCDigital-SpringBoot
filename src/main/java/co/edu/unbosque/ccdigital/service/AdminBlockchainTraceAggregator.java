package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.dto.FabricAuditEventView;
import co.edu.unbosque.ccdigital.dto.FabricDocView;
import co.edu.unbosque.ccdigital.entity.IdType;
import co.edu.unbosque.ccdigital.entity.Person;
import co.edu.unbosque.ccdigital.repository.PersonRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Agregador de trazabilidad blockchain (Fabric + Indy) para reportes administrativos.
 *
 * <p>Encapsula toda la lógica de consulta cruzada y normalización de eventos para mantener
 * {@link AdminReportService} enfocado en orquestación.</p>
 */
@Component
final class AdminBlockchainTraceAggregator {

    private static final ZoneId UI_ZONE = ZoneId.of("America/Bogota");

    private final PersonRepository personRepository;
    private final FabricLedgerCliService fabricLedgerCliService;
    private final FabricAuditCliService fabricAuditCliService;
    private final IndyProofLoginService indyProofLoginService;

    AdminBlockchainTraceAggregator(PersonRepository personRepository,
                                   FabricLedgerCliService fabricLedgerCliService,
                                   FabricAuditCliService fabricAuditCliService,
                                   IndyProofLoginService indyProofLoginService) {
        this.personRepository = personRepository;
        this.fabricLedgerCliService = fabricLedgerCliService;
        this.fabricAuditCliService = fabricAuditCliService;
        this.indyProofLoginService = indyProofLoginService;
    }

    /**
     * Construye la agregación blockchain con filtro por identificación o modo global.
     */
    TraceResult build(LocalDate from,
                      LocalDate to,
                      String traceIdTypeRaw,
                      String traceIdNumberRaw,
                      boolean traceAllRequested) {
        String traceIdType = normalize(traceIdTypeRaw).toUpperCase(Locale.ROOT);
        String traceIdNumber = normalize(traceIdNumberRaw);
        boolean allSelected = traceAllRequested;
        if (allSelected) {
            traceIdType = "";
            traceIdNumber = "";
        }
        boolean lookupRequested = allSelected || !traceIdType.isBlank() || !traceIdNumber.isBlank();

        if (!lookupRequested) {
            return TraceResult.empty(traceIdType, traceIdNumber);
        }
        if (allSelected) {
            return buildAllUsersBlockchainTrace(from, to);
        }
        if (traceIdType.isBlank() || traceIdNumber.isBlank()) {
            return TraceResult.withWarning(
                    traceIdType,
                    traceIdNumber,
                    "Para consultar trazabilidad blockchain debe ingresar tipo y número de identificación."
            );
        }

        IdType idType = parseIdType(traceIdType);
        if (idType == null) {
            return TraceResult.withWarning(
                    traceIdType,
                    traceIdNumber,
                    "El tipo de identificación indicado no es válido para la consulta de trazabilidad."
            );
        }

        String personLabel = personRepository.findByIdTypeAndIdNumber(idType, traceIdNumber)
                .map(this::resolvePersonLabel)
                .orElse(idType.name() + " " + traceIdNumber);

        List<AdminReportService.BlockchainTraceBlock> blocks = new ArrayList<>();
        String warningMessage = null;
        long fabricBlocks = 0L;
        long indyBlocks = 0L;

        try {
            List<FabricDocView> fabricDocs = fabricLedgerCliService.listDocsView(idType.name(), traceIdNumber);
            for (FabricDocView doc : fabricDocs) {
                LocalDateTime eventAt = parseFabricDate(doc.createdAt());
                if (!isInDateRange(eventAt, from, to)) {
                    continue;
                }
                blocks.add(new AdminReportService.BlockchainTraceBlock(
                        "Fabric",
                        normalizeOrFallback(doc.docId(), "Sin referencia"),
                        "Registro de documento",
                        "CONFIRMADO",
                        eventAt,
                        personLabel,
                        idType.name(),
                        traceIdNumber,
                        normalizeOrFallback(doc.title(), "Documento sin título"),
                        normalizeOrFallback(doc.issuingEntity(), "Entidad no identificada"),
                        "Documento anclado en ledger con hash y metadatos de integridad."
                ));
                fabricBlocks++;
            }

            List<FabricAuditEventView> auditEvents = fabricAuditCliService.listEventsForPerson(idType.name(), traceIdNumber);
            for (FabricAuditEventView event : auditEvents) {
                LocalDateTime eventAt = parseFabricDate(event.createdAt());
                if (!isInDateRange(eventAt, from, to)) {
                    continue;
                }
                blocks.add(new AdminReportService.BlockchainTraceBlock(
                        "Fabric",
                        normalizeOrFallback(event.txId(), "Sin referencia"),
                        resolveFabricAuditOperation(event),
                        resolveFabricAuditStatus(event),
                        eventAt,
                        personLabel,
                        normalizeOrFallback(event.idType(), idType.name()),
                        normalizeOrFallback(event.idNumber(), traceIdNumber),
                        normalizeOrFallback(event.documentTitle(), "Documento trazado"),
                        normalizeOrFallback(event.issuerName(), "Entidad no identificada"),
                        resolveFabricAuditDetail(event)
                ));
                fabricBlocks++;
            }
        } catch (Exception ex) {
            warningMessage = appendWarning(
                    warningMessage,
                    "No fue posible consultar trazabilidad en Fabric: " + rootMessage(ex)
            );
        }

        try {
            List<IndyProofLoginService.ProofTraceEvent> proofEvents = indyProofLoginService.listProofTraceEvents();
            for (IndyProofLoginService.ProofTraceEvent event : proofEvents) {
                if (!traceIdNumber.equals(normalize(event.idNumber()))) {
                    continue;
                }
                String eventIdType = normalize(event.idType()).toUpperCase(Locale.ROOT);
                if (!eventIdType.isBlank() && !idType.name().equals(eventIdType)) {
                    continue;
                }
                if (!isInDateRange(event.eventAt(), from, to)) {
                    continue;
                }
                String state = normalizeOrFallback(event.state(), "unknown");
                String status = Boolean.TRUE.equals(event.verified())
                        ? "VERIFICADO"
                        : ("done".equalsIgnoreCase(state) || "presentation-received".equalsIgnoreCase(state)
                        ? "NO VERIFICADO"
                        : state.toUpperCase(Locale.ROOT));

                blocks.add(new AdminReportService.BlockchainTraceBlock(
                        "Indy",
                        normalizeOrFallback(event.presExId(), "Sin referencia"),
                        "Verificación de credencial",
                        status,
                        event.eventAt(),
                        personLabel,
                        normalizeOrFallback(event.idType(), idType.name()),
                        traceIdNumber,
                        "Prueba de identidad",
                        "ACA-Py",
                        "Estado del intercambio: " + state
                ));
                indyBlocks++;
            }
        } catch (Exception ex) {
            warningMessage = appendWarning(
                    warningMessage,
                    "No fue posible consultar trazabilidad en Indy: " + rootMessage(ex)
            );
        }

        sortTraceBlocks(blocks);

        return new TraceResult(
                traceIdType,
                traceIdNumber,
                false,
                true,
                personLabel,
                warningMessage,
                fabricBlocks,
                indyBlocks,
                blocks
        );
    }

    private TraceResult buildAllUsersBlockchainTrace(LocalDate from, LocalDate to) {
        List<AdminReportService.BlockchainTraceBlock> blocks = new ArrayList<>();
        String warningMessage = null;
        long fabricBlocks = 0L;
        long indyBlocks = 0L;

        List<Person> people = personRepository.findAll();
        for (Person person : people) {
            if (person == null || person.getIdType() == null) {
                continue;
            }
            String idNumber = normalize(person.getIdNumber());
            if (idNumber.isBlank()) {
                continue;
            }
            String idType = person.getIdType().name();
            String personLabel = resolvePersonLabel(person);
            try {
                List<FabricDocView> fabricDocs = fabricLedgerCliService.listDocsView(idType, idNumber);
                for (FabricDocView doc : fabricDocs) {
                    LocalDateTime eventAt = parseFabricDate(doc.createdAt());
                    if (!isInDateRange(eventAt, from, to)) {
                        continue;
                    }
                    blocks.add(new AdminReportService.BlockchainTraceBlock(
                            "Fabric",
                            normalizeOrFallback(doc.docId(), "Sin referencia"),
                            "Registro de documento",
                            "CONFIRMADO",
                            eventAt,
                            personLabel,
                            idType,
                            idNumber,
                            normalizeOrFallback(doc.title(), "Documento sin título"),
                            normalizeOrFallback(doc.issuingEntity(), "Entidad no identificada"),
                            "Documento anclado en ledger con hash y metadatos de integridad."
                    ));
                    fabricBlocks++;
                }
            } catch (Exception ex) {
                warningMessage = appendWarning(
                        warningMessage,
                        "Algunos registros Fabric no pudieron consultarse: " + rootMessage(ex)
                );
            }
        }

        try {
            List<FabricAuditEventView> auditEvents = fabricAuditCliService.listAllEvents();
            for (FabricAuditEventView event : auditEvents) {
                LocalDateTime eventAt = parseFabricDate(event.createdAt());
                if (!isInDateRange(eventAt, from, to)) {
                    continue;
                }

                String idType = normalizeOrFallback(event.idType(), "N/A");
                String idNumber = normalizeOrFallback(event.idNumber(), "N/A");
                String personLabel = resolvePersonLabelById(idType, idNumber);

                blocks.add(new AdminReportService.BlockchainTraceBlock(
                        "Fabric",
                        normalizeOrFallback(event.txId(), "Sin referencia"),
                        resolveFabricAuditOperation(event),
                        resolveFabricAuditStatus(event),
                        eventAt,
                        personLabel,
                        idType,
                        idNumber,
                        normalizeOrFallback(event.documentTitle(), "Documento trazado"),
                        normalizeOrFallback(event.issuerName(), "Entidad no identificada"),
                        resolveFabricAuditDetail(event)
                ));
                fabricBlocks++;
            }
        } catch (Exception ex) {
            warningMessage = appendWarning(
                    warningMessage,
                    "No fue posible consultar auditoría Fabric global: " + rootMessage(ex)
            );
        }

        try {
            List<IndyProofLoginService.ProofTraceEvent> proofEvents = indyProofLoginService.listProofTraceEvents();
            for (IndyProofLoginService.ProofTraceEvent event : proofEvents) {
                if (!isInDateRange(event.eventAt(), from, to)) {
                    continue;
                }
                String state = normalizeOrFallback(event.state(), "unknown");
                String status = Boolean.TRUE.equals(event.verified())
                        ? "VERIFICADO"
                        : ("done".equalsIgnoreCase(state) || "presentation-received".equalsIgnoreCase(state)
                        ? "NO VERIFICADO"
                        : state.toUpperCase(Locale.ROOT));

                String idType = normalizeOrFallback(event.idType(), "N/A");
                String idNumber = normalizeOrFallback(event.idNumber(), "N/A");
                String personLabel = resolveProofPersonLabel(event, idType, idNumber);

                blocks.add(new AdminReportService.BlockchainTraceBlock(
                        "Indy",
                        normalizeOrFallback(event.presExId(), "Sin referencia"),
                        "Verificación de credencial",
                        status,
                        event.eventAt(),
                        personLabel,
                        idType,
                        idNumber,
                        "Prueba de identidad",
                        "ACA-Py",
                        "Estado del intercambio: " + state
                ));
                indyBlocks++;
            }
        } catch (Exception ex) {
            warningMessage = appendWarning(
                    warningMessage,
                    "No fue posible consultar trazabilidad en Indy: " + rootMessage(ex)
            );
        }

        sortTraceBlocks(blocks);

        return new TraceResult(
                "",
                "",
                true,
                true,
                "Todos los usuarios registrados",
                warningMessage,
                fabricBlocks,
                indyBlocks,
                blocks
        );
    }

    private String resolveProofPersonLabel(IndyProofLoginService.ProofTraceEvent event, String idType, String idNumber) {
        String firstName = normalize(event.firstName());
        String lastName = normalize(event.lastName());
        String fullName = (firstName + " " + lastName).trim();
        String idPart = (idType + " " + idNumber).trim();
        if (!fullName.isBlank() && !idPart.isBlank()) {
            return fullName + " (" + idPart + ")";
        }
        if (!fullName.isBlank()) {
            return fullName;
        }
        IdType parsedIdType = parseIdType(idType);
        if (parsedIdType != null && idNumber != null && !idNumber.isBlank() && !"N/A".equalsIgnoreCase(idNumber)) {
            return personRepository.findByIdTypeAndIdNumber(parsedIdType, idNumber)
                    .map(this::resolvePersonLabel)
                    .orElse(idPart.isBlank() ? "Usuario no identificado" : idPart);
        }
        return idPart.isBlank() ? "Usuario no identificado" : idPart;
    }

    private String resolvePersonLabelById(String idType, String idNumber) {
        String normalizedType = normalize(idType).toUpperCase(Locale.ROOT);
        String normalizedNumber = normalize(idNumber);
        if (normalizedType.isBlank() || normalizedNumber.isBlank() || "N/A".equalsIgnoreCase(normalizedNumber)) {
            return "Usuario no identificado";
        }

        IdType parsedIdType = parseIdType(normalizedType);
        if (parsedIdType == null) {
            return normalizedType + " " + normalizedNumber;
        }
        return personRepository.findByIdTypeAndIdNumber(parsedIdType, normalizedNumber)
                .map(this::resolvePersonLabel)
                .orElse(normalizedType + " " + normalizedNumber);
    }

    private String resolvePersonLabel(Person person) {
        if (person == null) {
            return "Persona no identificada";
        }
        String fullName = person.getFullName() == null || person.getFullName().isBlank()
                ? "Persona sin nombre"
                : person.getFullName().trim();
        String idType = person.getIdType() == null ? "" : person.getIdType().name();
        String idNumber = person.getIdNumber() == null ? "" : person.getIdNumber().trim();
        String idPart = (idType + " " + idNumber).trim();
        return idPart.isBlank() ? fullName : fullName + " (" + idPart + ")";
    }

    private String resolveFabricAuditOperation(FabricAuditEventView event) {
        String eventType = normalize(event.eventType()).toUpperCase(Locale.ROOT);
        return switch (eventType) {
            case "REQUEST_CREATED" -> "Solicitud de acceso creada";
            case "DOC_VERIFY_ON_REQUEST" -> "Verificación de documento en solicitud";
            case "DOC_VIEW_GRANTED" -> "Visualización de documento autorizada";
            case "DOC_DOWNLOAD_GRANTED" -> "Descarga de documento autorizada";
            case "DOC_ACCESS_CHECK" -> "Verificación de acceso a documento";
            case "DOC_BLOCK_TRACE_QUERY" -> "Consulta de trazabilidad de bloque";
            default -> eventType.isBlank() ? "Evento de auditoría Fabric" : eventType;
        };
    }

    private String resolveFabricAuditStatus(FabricAuditEventView event) {
        String result = normalize(event.result()).toUpperCase(Locale.ROOT);
        if (result.isBlank()) {
            return "REGISTRADO";
        }
        return switch (result) {
            case "OK", "SUCCESS" -> "CONFIRMADO";
            case "FAIL", "ERROR", "DENIED" -> "FALLIDO";
            default -> result;
        };
    }

    private String resolveFabricAuditDetail(FabricAuditEventView event) {
        String action = normalize(event.action());
        String reason = normalize(event.reason());
        String requestId = normalize(event.requestId());
        String personDocumentId = normalize(event.personDocumentId());

        List<String> parts = new ArrayList<>();
        if (!action.isBlank()) {
            parts.add("Acción: " + action);
        }
        if (!requestId.isBlank()) {
            parts.add("Solicitud: " + requestId);
        }
        if (!personDocumentId.isBlank()) {
            parts.add("Documento local: " + personDocumentId);
        }
        if (!reason.isBlank()) {
            parts.add(reason);
        }
        return parts.isEmpty() ? "Evento de auditoría de acceso registrado en Fabric." : String.join(" · ", parts);
    }

    private void sortTraceBlocks(List<AdminReportService.BlockchainTraceBlock> blocks) {
        blocks.sort(Comparator
                .comparing(AdminReportService.BlockchainTraceBlock::getEventAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(AdminReportService.BlockchainTraceBlock::getNetwork)
                .thenComparing(AdminReportService.BlockchainTraceBlock::getBlockRef));
    }

    private LocalDateTime parseFabricDate(String createdAtRaw) {
        if (createdAtRaw == null || createdAtRaw.isBlank()) {
            return null;
        }
        String raw = createdAtRaw.trim();
        try {
            return OffsetDateTime.parse(raw).atZoneSameInstant(UI_ZONE).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // Puede venir como Instant sin offset explícito.
        }
        try {
            return Instant.parse(raw).atZone(UI_ZONE).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // Se intenta parseo local sin zona.
        }
        try {
            String normalized = raw.replace(' ', 'T');
            // Si Fabric entrega fecha sin zona, se asume UTC para evitar mostrar +5h en UI.
            LocalDateTime utcLocalDateTime = LocalDateTime.parse(normalized);
            return utcLocalDateTime.atOffset(ZoneOffset.UTC).atZoneSameInstant(UI_ZONE).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private boolean isInDateRange(LocalDateTime value, LocalDate from, LocalDate to) {
        if (value == null) {
            return false;
        }
        LocalDate day = value.toLocalDate();
        return !day.isBefore(from) && !day.isAfter(to);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeOrFallback(String value, String fallback) {
        String normalized = normalize(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private IdType parseIdType(String value) {
        try {
            return IdType.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String appendWarning(String current, String incoming) {
        if (incoming == null || incoming.isBlank()) {
            return current;
        }
        if (current == null || current.isBlank()) {
            return incoming;
        }
        return current + " " + incoming;
    }

    private String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        return normalizeOrFallback(cursor.getMessage(), "Error no detallado.");
    }

    /**
     * Resultado de agregación de trazabilidad blockchain para el dashboard.
     */
    record TraceResult(
            String traceIdType,
            String traceIdNumber,
            boolean allSelected,
            boolean lookupRequested,
            String personLabel,
            String warningMessage,
            long fabricBlocks,
            long indyBlocks,
            List<AdminReportService.BlockchainTraceBlock> blocks
    ) {
        private static TraceResult empty(String traceIdType, String traceIdNumber) {
            return new TraceResult(traceIdType, traceIdNumber, false, false, "", null, 0L, 0L, List.of());
        }

        private static TraceResult withWarning(String traceIdType, String traceIdNumber, String warning) {
            return new TraceResult(traceIdType, traceIdNumber, false, true, "", warning, 0L, 0L, List.of());
        }
    }
}
