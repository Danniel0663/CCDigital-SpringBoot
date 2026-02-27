package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.entity.AccessRequest;
import co.edu.unbosque.ccdigital.entity.AccessRequestItem;
import co.edu.unbosque.ccdigital.entity.AccessRequestStatus;
import co.edu.unbosque.ccdigital.entity.Person;
import co.edu.unbosque.ccdigital.entity.PersonDocument;
import co.edu.unbosque.ccdigital.repository.AccessRequestRepository;
import co.edu.unbosque.ccdigital.repository.PersonDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Servicio de trazabilidad para el módulo Admin > Reportes.
 *
 * <p>Consolida métricas de uso del sistema sin modificar la lógica transaccional actual:
 * toma la información existente de solicitudes de acceso y documentos registrados, aplica filtros
 * de rango temporal y genera indicadores, tendencias y tablas Top para la UI administrativa.</p>
 *
 * @since 3.0
 */
@Service
public class AdminReportService {

    private static final int TOP_LIMIT = 10;
    private static final Locale LOCALE_ES_CO = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter DAY_LABEL_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AccessRequestRepository accessRequestRepository;
    private final PersonDocumentRepository personDocumentRepository;

    /**
     * Constructor del servicio de reportes administrativos.
     *
     * @param accessRequestRepository repositorio de solicitudes de acceso
     * @param personDocumentRepository repositorio de documentos de persona
     */
    public AdminReportService(AccessRequestRepository accessRequestRepository,
                              PersonDocumentRepository personDocumentRepository) {
        this.accessRequestRepository = accessRequestRepository;
        this.personDocumentRepository = personDocumentRepository;
    }

    /**
     * Granularidad de agrupación temporal para la sección de tendencias.
     */
    public enum TrendPeriod {
        DAY("Día"),
        WEEK("Semana"),
        MONTH("Mes");

        private final String label;

        TrendPeriod(String label) {
            this.label = label;
        }

        /**
         * @return etiqueta legible para la UI del filtro
         */
        public String getLabel() {
            return label;
        }

        /**
         * Convierte el valor textual del query param en una granularidad válida.
         *
         * @param raw texto recibido desde la URL
         * @return período válido; por defecto {@link #DAY}
         */
        public static TrendPeriod from(String raw) {
            if (raw == null || raw.isBlank()) {
                return DAY;
            }
            try {
                return TrendPeriod.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
                return DAY;
            }
        }

        private LocalDate bucketStart(LocalDate date) {
            if (this == WEEK) {
                return date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            }
            if (this == MONTH) {
                return date.withDayOfMonth(1);
            }
            return date;
        }

        private LocalDate nextBucket(LocalDate bucketStart) {
            if (this == WEEK) {
                return bucketStart.plusWeeks(1);
            }
            if (this == MONTH) {
                return bucketStart.plusMonths(1);
            }
            return bucketStart.plusDays(1);
        }

        private String formatBucketLabel(LocalDate bucketStart) {
            if (this == WEEK) {
                LocalDate bucketEnd = bucketStart.plusDays(6);
                return DAY_LABEL_FORMATTER.format(bucketStart) + " - " + DAY_LABEL_FORMATTER.format(bucketEnd);
            }
            if (this == MONTH) {
                String month = bucketStart.format(DateTimeFormatter.ofPattern("MMMM yyyy", LOCALE_ES_CO));
                return month.substring(0, 1).toUpperCase(LOCALE_ES_CO) + month.substring(1);
            }
            return DAY_LABEL_FORMATTER.format(bucketStart);
        }
    }

    /**
     * Fila de indicador "Top" para tablas de ranking.
     */
    public static final class TopRow {
        private final String label;
        private final long total;

        public TopRow(String label, long total) {
            this.label = label;
            this.total = total;
        }

        public String getLabel() {
            return label;
        }

        public long getTotal() {
            return total;
        }
    }

    /**
     * Fila de tendencia agregada por bucket temporal.
     */
    public static final class TrendRow {
        private final String periodLabel;
        private final long totalRequests;
        private final long successfulRequests;
        private final long unsuccessfulRequests;
        private final long documentsConsulted;
        private final long documentsDeposited;

        public TrendRow(String periodLabel,
                        long totalRequests,
                        long successfulRequests,
                        long unsuccessfulRequests,
                        long documentsConsulted,
                        long documentsDeposited) {
            this.periodLabel = periodLabel;
            this.totalRequests = totalRequests;
            this.successfulRequests = successfulRequests;
            this.unsuccessfulRequests = unsuccessfulRequests;
            this.documentsConsulted = documentsConsulted;
            this.documentsDeposited = documentsDeposited;
        }

        public String getPeriodLabel() {
            return periodLabel;
        }

        public long getTotalRequests() {
            return totalRequests;
        }

        public long getSuccessfulRequests() {
            return successfulRequests;
        }

        public long getUnsuccessfulRequests() {
            return unsuccessfulRequests;
        }

        public long getDocumentsConsulted() {
            return documentsConsulted;
        }

        public long getDocumentsDeposited() {
            return documentsDeposited;
        }
    }

    /**
     * Resultado consolidado del dashboard de reportes.
     */
    public static final class DashboardReport {
        private final LocalDate fromDate;
        private final LocalDate toDate;
        private final TrendPeriod period;
        private final long totalRequests;
        private final long successfulRequests;
        private final long unsuccessfulRequests;
        private final long documentsConsulted;
        private final long documentsDeposited;
        private final List<TrendRow> trendRows;
        private final List<TopRow> topDocuments;
        private final List<TopRow> topPeople;
        private final List<TopRow> topIssuers;
        private final List<TopRow> topErrors;

        public DashboardReport(LocalDate fromDate,
                               LocalDate toDate,
                               TrendPeriod period,
                               long totalRequests,
                               long successfulRequests,
                               long unsuccessfulRequests,
                               long documentsConsulted,
                               long documentsDeposited,
                               List<TrendRow> trendRows,
                               List<TopRow> topDocuments,
                               List<TopRow> topPeople,
                               List<TopRow> topIssuers,
                               List<TopRow> topErrors) {
            this.fromDate = fromDate;
            this.toDate = toDate;
            this.period = period;
            this.totalRequests = totalRequests;
            this.successfulRequests = successfulRequests;
            this.unsuccessfulRequests = unsuccessfulRequests;
            this.documentsConsulted = documentsConsulted;
            this.documentsDeposited = documentsDeposited;
            this.trendRows = trendRows;
            this.topDocuments = topDocuments;
            this.topPeople = topPeople;
            this.topIssuers = topIssuers;
            this.topErrors = topErrors;
        }

        public LocalDate getFromDate() {
            return fromDate;
        }

        public LocalDate getToDate() {
            return toDate;
        }

        public TrendPeriod getPeriod() {
            return period;
        }

        public long getTotalRequests() {
            return totalRequests;
        }

        public long getSuccessfulRequests() {
            return successfulRequests;
        }

        public long getUnsuccessfulRequests() {
            return unsuccessfulRequests;
        }

        public long getDocumentsConsulted() {
            return documentsConsulted;
        }

        public long getDocumentsDeposited() {
            return documentsDeposited;
        }

        public List<TrendRow> getTrendRows() {
            return trendRows;
        }

        public List<TopRow> getTopDocuments() {
            return topDocuments;
        }

        public List<TopRow> getTopPeople() {
            return topPeople;
        }

        public List<TopRow> getTopIssuers() {
            return topIssuers;
        }

        public List<TopRow> getTopErrors() {
            return topErrors;
        }
    }

    /**
     * Construye el dataset del dashboard de trazabilidad para Admin > Reportes.
     *
     * <p>Reglas de cálculo principales:</p>
     * <ul>
     *   <li>Solicitudes exitosas: estado {@code APROBADA}.</li>
     *   <li>Solicitudes no exitosas: cualquier estado diferente de {@code APROBADA}.</li>
     *   <li>Documentos consultados/vistos: suma de ítems asociados a solicitudes aprobadas.</li>
     *   <li>Documentos depositados/registrados: cantidad de {@link PersonDocument} creados en rango.</li>
     * </ul>
     *
     * @param fromDate fecha inicial (incluyente). Si es null, usa últimos 30 días
     * @param toDate fecha final (incluyente). Si es null, usa hoy
     * @param periodRaw granularidad textual (DAY/WEEK/MONTH)
     * @return objeto consolidado para renderizar el dashboard
     */
    @Transactional(readOnly = true)
    public DashboardReport buildDashboard(LocalDate fromDate, LocalDate toDate, String periodRaw) {
        LocalDate to = (toDate != null) ? toDate : LocalDate.now();
        LocalDate from = (fromDate != null) ? fromDate : to.minusDays(29);
        if (from.isAfter(to)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }

        TrendPeriod period = TrendPeriod.from(periodRaw);
        LocalDateTime fromStart = from.atStartOfDay();
        LocalDateTime toEndExclusive = to.plusDays(1).atStartOfDay();

        // Se cargan datos con detalle para evitar subconsultas durante agregaciones.
        List<AccessRequest> allRequests = accessRequestRepository.findAllWithDetailsForReport();
        List<PersonDocument> allPersonDocuments = personDocumentRepository.findAll();

        List<AccessRequest> requestsInRange = allRequests.stream()
                .filter(r -> isInDateTimeRange(r.getRequestedAt(), fromStart, toEndExclusive))
                .toList();

        long totalRequests = requestsInRange.size();
        long successfulRequests = requestsInRange.stream()
                .filter(r -> r.getStatus() == AccessRequestStatus.APROBADA)
                .count();
        long unsuccessfulRequests = totalRequests - successfulRequests;
        long documentsConsulted = requestsInRange.stream()
                .filter(r -> r.getStatus() == AccessRequestStatus.APROBADA)
                .mapToLong(this::safeItemsCount)
                .sum();
        long documentsDeposited = allPersonDocuments.stream()
                .filter(pd -> isInDateTimeRange(pd.getCreatedAt(), fromStart, toEndExclusive))
                .count();

        LinkedHashMap<LocalDate, BucketAccumulator> buckets = initBuckets(from, to, period);
        aggregateRequestTrends(requestsInRange, buckets, period);
        aggregateDepositTrends(allPersonDocuments, fromStart, toEndExclusive, buckets, period);

        List<TrendRow> trendRows = buckets.values().stream()
                .map(acc -> new TrendRow(
                        acc.label,
                        acc.totalRequests,
                        acc.successfulRequests,
                        acc.unsuccessfulRequests,
                        acc.documentsConsulted,
                        acc.documentsDeposited
                ))
                .toList();

        Map<String, Long> topDocuments = new LinkedHashMap<>();
        Map<String, Long> topPeople = new LinkedHashMap<>();
        Map<String, Long> topIssuers = new LinkedHashMap<>();
        Map<String, Long> topErrors = new LinkedHashMap<>();
        aggregateTopTables(requestsInRange, topDocuments, topPeople, topIssuers, topErrors);

        return new DashboardReport(
                from,
                to,
                period,
                totalRequests,
                successfulRequests,
                unsuccessfulRequests,
                documentsConsulted,
                documentsDeposited,
                trendRows,
                toTopRows(topDocuments, TOP_LIMIT),
                toTopRows(topPeople, TOP_LIMIT),
                toTopRows(topIssuers, TOP_LIMIT),
                toTopRows(topErrors, TOP_LIMIT)
        );
    }

    /**
     * Agrega la porción de tendencia asociada a solicitudes de acceso.
     */
    private void aggregateRequestTrends(List<AccessRequest> requests,
                                        Map<LocalDate, BucketAccumulator> buckets,
                                        TrendPeriod period) {
        for (AccessRequest request : requests) {
            LocalDateTime requestedAt = request.getRequestedAt();
            if (requestedAt == null) {
                continue;
            }
            LocalDate bucketKey = period.bucketStart(requestedAt.toLocalDate());
            BucketAccumulator bucket = buckets.get(bucketKey);
            if (bucket == null) {
                continue;
            }

            bucket.totalRequests++;
            if (request.getStatus() == AccessRequestStatus.APROBADA) {
                bucket.successfulRequests++;
                bucket.documentsConsulted += safeItemsCount(request);
            } else {
                bucket.unsuccessfulRequests++;
            }
        }
    }

    /**
     * Agrega la porción de tendencia asociada a documentos depositados en el rango.
     */
    private void aggregateDepositTrends(List<PersonDocument> personDocuments,
                                        LocalDateTime fromStart,
                                        LocalDateTime toEndExclusive,
                                        Map<LocalDate, BucketAccumulator> buckets,
                                        TrendPeriod period) {
        for (PersonDocument pd : personDocuments) {
            LocalDateTime createdAt = pd.getCreatedAt();
            if (!isInDateTimeRange(createdAt, fromStart, toEndExclusive)) {
                continue;
            }
            LocalDate bucketKey = period.bucketStart(createdAt.toLocalDate());
            BucketAccumulator bucket = buckets.get(bucketKey);
            if (bucket != null) {
                bucket.documentsDeposited++;
            }
        }
    }

    /**
     * Consolida tablas "Top" para documentos, personas, emisores y fallos normalizados.
     */
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

    /**
     * Inicializa buckets vacíos para garantizar continuidad visual de la tendencia.
     */
    private LinkedHashMap<LocalDate, BucketAccumulator> initBuckets(LocalDate from,
                                                                     LocalDate to,
                                                                     TrendPeriod period) {
        LinkedHashMap<LocalDate, BucketAccumulator> buckets = new LinkedHashMap<>();
        LocalDate cursor = period.bucketStart(from);
        LocalDate end = period.bucketStart(to);

        while (!cursor.isAfter(end)) {
            buckets.put(cursor, new BucketAccumulator(period.formatBucketLabel(cursor)));
            cursor = period.nextBucket(cursor);
        }

        return buckets;
    }

    /**
     * Evalúa rango temporal incluyente/excluyente: [fromInclusive, toExclusive).
     */
    private boolean isInDateTimeRange(LocalDateTime value, LocalDateTime fromInclusive, LocalDateTime toExclusive) {
        return value != null && !value.isBefore(fromInclusive) && value.isBefore(toExclusive);
    }

    /**
     * Cuenta ítems de forma segura para solicitudes con relaciones nulas.
     */
    private long safeItemsCount(AccessRequest request) {
        return safeItems(request).size();
    }

    /**
     * Retorna colección de ítems nunca nula para simplificar agregaciones.
     */
    private List<AccessRequestItem> safeItems(AccessRequest request) {
        if (request == null || request.getItems() == null) {
            return List.of();
        }
        return request.getItems();
    }

    /**
     * Resuelve etiqueta amigable del documento para rankings.
     */
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

    /**
     * Resuelve etiqueta de persona conservando nombre y documento cuando existan.
     */
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

    /**
     * Resuelve nombre de emisor con fallback estable para datos incompletos.
     */
    private String resolveIssuerLabel(AccessRequest request) {
        if (request == null || request.getEntity() == null || request.getEntity().getName() == null
                || request.getEntity().getName().isBlank()) {
            return "Emisor no identificado";
        }
        return request.getEntity().getName().trim();
    }

    /**
     * Normaliza el motivo de fallo para facilitar ranking de errores.
     */
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

    /**
     * Acumula contador por etiqueta normalizada.
     */
    private void add(Map<String, Long> map, String label, long value) {
        String key = (label == null || label.isBlank()) ? "No identificado" : label.trim();
        map.merge(key, value, (left, right) -> (left == null ? 0L : left) + (right == null ? 0L : right));
    }

    /**
     * Ordena descendente y limita resultados para visualización Top N.
     */
    private List<TopRow> toTopRows(Map<String, Long> source, int limit) {
        List<Map.Entry<String, Long>> ordered = new ArrayList<>(source.entrySet());
        ordered.sort(Comparator
                .<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue)
                .reversed()
                .thenComparing(Map.Entry::getKey));

        List<TopRow> out = new ArrayList<>();
        int max = Math.max(1, limit);
        for (Map.Entry<String, Long> entry : ordered) {
            if (entry.getValue() == null || entry.getValue() <= 0L) {
                continue;
            }
            out.add(new TopRow(entry.getKey(), entry.getValue()));
            if (out.size() >= max) {
                break;
            }
        }
        return out;
    }

    /**
     * Acumulador interno de métricas por bucket temporal.
     */
    private static final class BucketAccumulator {
        private final String label;
        private long totalRequests;
        private long successfulRequests;
        private long unsuccessfulRequests;
        private long documentsConsulted;
        private long documentsDeposited;

        private BucketAccumulator(String label) {
            this.label = label;
        }
    }
}
