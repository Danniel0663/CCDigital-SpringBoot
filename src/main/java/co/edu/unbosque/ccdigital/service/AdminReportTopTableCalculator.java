package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.entity.AccessRequest;
import co.edu.unbosque.ccdigital.entity.AccessRequestItem;
import co.edu.unbosque.ccdigital.entity.AccessRequestStatus;
import co.edu.unbosque.ccdigital.entity.Person;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calculadora de tablas Top para dashboard administrativo.
 *
 * <p>Centraliza la lógica de ranking para documentos, personas, emisores y errores.</p>
 */
@Component
final class AdminReportTopTableCalculator {

    /**
     * Construye todas las tablas Top con el límite solicitado.
     */
    TopTables compute(List<AccessRequest> requests, int limit) {
        Map<String, Long> topDocuments = new LinkedHashMap<>();
        Map<String, Long> topPeople = new LinkedHashMap<>();
        Map<String, Long> topIssuers = new LinkedHashMap<>();
        Map<String, Long> topErrors = new LinkedHashMap<>();
        aggregateTopTables(requests, topDocuments, topPeople, topIssuers, topErrors);

        return new TopTables(
                toTopRows(topDocuments, limit),
                toTopRows(topPeople, limit),
                toTopRows(topIssuers, limit),
                toTopRows(topErrors, limit)
        );
    }

    private void aggregateTopTables(List<AccessRequest> requests,
                                    Map<String, Long> topDocuments,
                                    Map<String, Long> topPeople,
                                    Map<String, Long> topIssuers,
                                    Map<String, Long> topErrors) {
        for (AccessRequest request : requests) {
            add(topIssuers, resolveIssuerLabel(request), 1L);

            if (request.getStatus() == AccessRequestStatus.APROBADA) {
                long consultedDocsForPerson = 0L;
                for (AccessRequestItem item : safeItems(request)) {
                    add(topDocuments, resolveDocumentLabel(item), 1L);
                    consultedDocsForPerson++;
                }
                add(topPeople, resolvePersonLabel(request.getPerson()), Math.max(consultedDocsForPerson, 1L));
                continue;
            }

            if (request.getStatus() == AccessRequestStatus.RECHAZADA
                    || request.getStatus() == AccessRequestStatus.EXPIRADA) {
                add(topErrors, resolveFailureLabel(request), 1L);
            }
        }
    }

    private List<AccessRequestItem> safeItems(AccessRequest request) {
        if (request == null || request.getItems() == null) {
            return List.of();
        }
        return request.getItems();
    }

    private String resolveDocumentLabel(AccessRequestItem item) {
        if (item == null || item.getPersonDocument() == null || item.getPersonDocument().getDocumentDefinition() == null) {
            return "Documento no identificado";
        }
        String title = item.getPersonDocument().getDocumentDefinition().getTitle();
        if (title == null || title.isBlank()) {
            Long id = item.getPersonDocument().getId();
            return id == null ? "Documento sin título" : "Documento #" + id;
        }
        return title.trim();
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

    private String resolveIssuerLabel(AccessRequest request) {
        if (request == null || request.getEntity() == null || request.getEntity().getName() == null
                || request.getEntity().getName().isBlank()) {
            return "Emisor no identificado";
        }
        return request.getEntity().getName().trim();
    }

    private String resolveFailureLabel(AccessRequest request) {
        if (request == null || request.getStatus() == null) {
            return "Fallo no clasificado";
        }
        if (request.getStatus() == AccessRequestStatus.EXPIRADA) {
            return "Solicitud expirada";
        }
        if (request.getStatus() == AccessRequestStatus.RECHAZADA) {
            String note = request.getDecisionNote();
            if (note == null || note.isBlank()) {
                return "Rechazada por el usuario";
            }
            String normalized = note.trim().replaceAll("\\s+", " ");
            if (normalized.length() > 90) {
                normalized = normalized.substring(0, 90) + "...";
            }
            return "Rechazada: " + normalized;
        }
        return "No exitosa (" + request.getStatus().name() + ")";
    }

    private void add(Map<String, Long> map, String label, long value) {
        String key = (label == null || label.isBlank()) ? "No identificado" : label.trim();
        map.merge(key, value, (left, right) -> (left == null ? 0L : left) + (right == null ? 0L : right));
    }

    private List<AdminReportService.TopRow> toTopRows(Map<String, Long> source, int limit) {
        List<Map.Entry<String, Long>> ordered = new ArrayList<>(source.entrySet());
        ordered.sort(Comparator
                .<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue)
                .reversed()
                .thenComparing(Map.Entry::getKey));

        List<AdminReportService.TopRow> out = new ArrayList<>();
        int max = Math.max(1, limit);
        for (Map.Entry<String, Long> entry : ordered) {
            if (entry.getValue() == null || entry.getValue() <= 0L) {
                continue;
            }
            out.add(new AdminReportService.TopRow(entry.getKey(), entry.getValue()));
            if (out.size() >= max) {
                break;
            }
        }
        return out;
    }

    /**
     * Contenedor de tablas Top ya ordenadas y limitadas.
     */
    record TopTables(
            List<AdminReportService.TopRow> topDocuments,
            List<AdminReportService.TopRow> topPeople,
            List<AdminReportService.TopRow> topIssuers,
            List<AdminReportService.TopRow> topErrors
    ) {
    }
}
