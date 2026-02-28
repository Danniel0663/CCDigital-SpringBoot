package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.dto.FabricDocView;
import co.edu.unbosque.ccdigital.entity.AccessRequest;
import co.edu.unbosque.ccdigital.entity.AccessRequestItem;
import co.edu.unbosque.ccdigital.entity.AccessRequestStatus;
import co.edu.unbosque.ccdigital.entity.IdType;
import co.edu.unbosque.ccdigital.entity.Person;
import co.edu.unbosque.ccdigital.entity.PersonDocument;
import co.edu.unbosque.ccdigital.repository.AccessRequestRepository;
import co.edu.unbosque.ccdigital.repository.PersonRepository;
import co.edu.unbosque.ccdigital.repository.PersonDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
    private static final DateTimeFormatter DATE_TIME_LABEL_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final ZoneId UI_ZONE = ZoneId.of("America/Bogota");

    private final AccessRequestRepository accessRequestRepository;
    private final PersonDocumentRepository personDocumentRepository;
    private final PersonRepository personRepository;
    private final FabricLedgerCliService fabricLedgerCliService;
    private final IndyProofLoginService indyProofLoginService;

    /**
     * Constructor del servicio de reportes administrativos.
     *
     * @param accessRequestRepository repositorio de solicitudes de acceso
     * @param personDocumentRepository repositorio de documentos de persona
     * @param personRepository repositorio de personas para resolver trazabilidad por usuario
     * @param fabricLedgerCliService servicio de consulta de documentos en ledger Fabric
     * @param indyProofLoginService servicio de consulta de records de verificación Indy
     */
    public AdminReportService(AccessRequestRepository accessRequestRepository,
                              PersonDocumentRepository personDocumentRepository,
                              PersonRepository personRepository,
                              FabricLedgerCliService fabricLedgerCliService,
                              IndyProofLoginService indyProofLoginService) {
        this.accessRequestRepository = accessRequestRepository;
        this.personDocumentRepository = personDocumentRepository;
        this.personRepository = personRepository;
        this.fabricLedgerCliService = fabricLedgerCliService;
        this.indyProofLoginService = indyProofLoginService;
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
     * Bloque de trazabilidad blockchain para vista interactiva del dashboard Admin.
     */
    public static final class BlockchainTraceBlock {
        private final String network;
        private final String blockRef;
        private final String operation;
        private final String status;
        private final LocalDateTime eventAt;
        private final String eventAtLabel;
        private final String personLabel;
        private final String idType;
        private final String idNumber;
        private final String documentTitle;
        private final String issuer;
        private final String detail;

        public BlockchainTraceBlock(String network,
                                    String blockRef,
                                    String operation,
                                    String status,
                                    LocalDateTime eventAt,
                                    String personLabel,
                                    String idType,
                                    String idNumber,
                                    String documentTitle,
                                    String issuer,
                                    String detail) {
            this.network = network;
            this.blockRef = blockRef;
            this.operation = operation;
            this.status = status;
            this.eventAt = eventAt;
            this.eventAtLabel = eventAt == null ? "Sin fecha" : DATE_TIME_LABEL_FORMATTER.format(eventAt);
            this.personLabel = personLabel;
            this.idType = idType;
            this.idNumber = idNumber;
            this.documentTitle = documentTitle;
            this.issuer = issuer;
            this.detail = detail;
        }

        public String getNetwork() {
            return network;
        }

        public String getBlockRef() {
            return blockRef;
        }

        public String getOperation() {
            return operation;
        }

        public String getStatus() {
            return status;
        }

        public LocalDateTime getEventAt() {
            return eventAt;
        }

        public String getEventAtLabel() {
            return eventAtLabel;
        }

        public String getPersonLabel() {
            return personLabel;
        }

        public String getIdType() {
            return idType;
        }

        public String getIdNumber() {
            return idNumber;
        }

        public String getDocumentTitle() {
            return documentTitle;
        }

        public String getIssuer() {
            return issuer;
        }

        public String getDetail() {
            return detail;
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
        private final String traceIdType;
        private final String traceIdNumber;
        private final boolean traceAllSelected;
        private final boolean traceLookupRequested;
        private final String tracePersonLabel;
        private final String traceWarningMessage;
        private final long fabricTraceBlocks;
        private final long indyTraceBlocks;
        private final List<BlockchainTraceBlock> blockchainBlocks;

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
                               List<TopRow> topErrors,
                               String traceIdType,
                               String traceIdNumber,
                               boolean traceAllSelected,
                               boolean traceLookupRequested,
                               String tracePersonLabel,
                               String traceWarningMessage,
                               long fabricTraceBlocks,
                               long indyTraceBlocks,
                               List<BlockchainTraceBlock> blockchainBlocks) {
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
            this.traceIdType = traceIdType;
            this.traceIdNumber = traceIdNumber;
            this.traceAllSelected = traceAllSelected;
            this.traceLookupRequested = traceLookupRequested;
            this.tracePersonLabel = tracePersonLabel;
            this.traceWarningMessage = traceWarningMessage;
            this.fabricTraceBlocks = fabricTraceBlocks;
            this.indyTraceBlocks = indyTraceBlocks;
            this.blockchainBlocks = blockchainBlocks;
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

        public String getTraceIdType() {
            return traceIdType;
        }

        public String getTraceIdNumber() {
            return traceIdNumber;
        }

        public boolean isTraceAllSelected() {
            return traceAllSelected;
        }

        public boolean isTraceLookupRequested() {
            return traceLookupRequested;
        }

        public String getTracePersonLabel() {
            return tracePersonLabel;
        }

        public String getTraceWarningMessage() {
            return traceWarningMessage;
        }

        public long getFabricTraceBlocks() {
            return fabricTraceBlocks;
        }

        public long getIndyTraceBlocks() {
            return indyTraceBlocks;
        }

        public List<BlockchainTraceBlock> getBlockchainBlocks() {
            return blockchainBlocks;
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
        return buildDashboard(fromDate, toDate, periodRaw, null, null);
    }

    /**
     * Construye el dashboard de reportes e incluye trazabilidad blockchain opcional.
     *
     * <p>Si se envían {@code traceIdType} y {@code traceIdNumber}, además de KPIs/tablas del
     * sistema se consulta trazabilidad en Fabric e Indy para el usuario indicado.</p>
     *
     * @param fromDate fecha inicial (incluyente). Si es null, usa últimos 30 días
     * @param toDate fecha final (incluyente). Si es null, usa hoy
     * @param periodRaw granularidad textual (DAY/WEEK/MONTH)
     * @param traceIdType tipo de identificación para consulta blockchain (opcional)
     * @param traceIdNumber número de identificación para consulta blockchain (opcional)
     * @return objeto consolidado para renderizar el dashboard
     */
    @Transactional(readOnly = true)
    public DashboardReport buildDashboard(LocalDate fromDate,
                                          LocalDate toDate,
                                          String periodRaw,
                                          String traceIdType,
                                          String traceIdNumber) {
        return buildDashboard(fromDate, toDate, periodRaw, traceIdType, traceIdNumber, false);
    }

    /**
     * Variante del dashboard de reportes con control explícito para consulta blockchain "ver todos".
     *
     * @param fromDate fecha inicial (incluyente). Si es null, usa últimos 30 días
     * @param toDate fecha final (incluyente). Si es null, usa hoy
     * @param periodRaw granularidad textual (DAY/WEEK/MONTH)
     * @param traceIdType tipo de identificación para consulta blockchain (opcional)
     * @param traceIdNumber número de identificación para consulta blockchain (opcional)
     * @param traceAllRequested si es true, consulta trazabilidad global sin exigir identificación
     * @return objeto consolidado para renderizar el dashboard
     */
    @Transactional(readOnly = true)
    public DashboardReport buildDashboard(LocalDate fromDate,
                                          LocalDate toDate,
                                          String periodRaw,
                                          String traceIdType,
                                          String traceIdNumber,
                                          boolean traceAllRequested) {
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

        TraceAggregation trace = buildBlockchainTrace(from, to, traceIdType, traceIdNumber, traceAllRequested);

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
                toTopRows(topErrors, TOP_LIMIT),
                trace.traceIdType(),
                trace.traceIdNumber(),
                trace.allSelected(),
                trace.lookupRequested(),
                trace.personLabel(),
                trace.warningMessage(),
                trace.fabricBlocks(),
                trace.indyBlocks(),
                trace.blocks()
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
     * Consolida trazabilidad blockchain para un usuario específico, consultando Fabric e Indy.
     */
    private TraceAggregation buildBlockchainTrace(LocalDate from,
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
            return TraceAggregation.empty(traceIdType, traceIdNumber);
        }
        if (allSelected) {
            return buildAllUsersBlockchainTrace(from, to);
        }
        if (traceIdType.isBlank() || traceIdNumber.isBlank()) {
            return TraceAggregation.withWarning(
                    traceIdType,
                    traceIdNumber,
                    "Para consultar trazabilidad blockchain debe ingresar tipo y número de identificación."
            );
        }

        IdType idType = parseIdType(traceIdType);
        if (idType == null) {
            return TraceAggregation.withWarning(
                    traceIdType,
                    traceIdNumber,
                    "El tipo de identificación indicado no es válido para la consulta de trazabilidad."
            );
        }

        String personLabel = personRepository.findByIdTypeAndIdNumber(idType, traceIdNumber)
                .map(this::resolvePersonLabel)
                .orElse(idType.name() + " " + traceIdNumber);

        List<BlockchainTraceBlock> blocks = new ArrayList<>();
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
                blocks.add(new BlockchainTraceBlock(
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

                blocks.add(new BlockchainTraceBlock(
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

        blocks.sort(Comparator
                .comparing(BlockchainTraceBlock::getEventAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(BlockchainTraceBlock::getNetwork)
                .thenComparing(BlockchainTraceBlock::getBlockRef));

        return new TraceAggregation(
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

    /**
     * Consolida trazabilidad blockchain global para todas las personas registradas (sin filtro por cédula).
     */
    private TraceAggregation buildAllUsersBlockchainTrace(LocalDate from, LocalDate to) {
        List<BlockchainTraceBlock> blocks = new ArrayList<>();
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
                    blocks.add(new BlockchainTraceBlock(
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

                blocks.add(new BlockchainTraceBlock(
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

        blocks.sort(Comparator
                .comparing(BlockchainTraceBlock::getEventAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(BlockchainTraceBlock::getNetwork)
                .thenComparing(BlockchainTraceBlock::getBlockRef));

        return new TraceAggregation(
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

    /**
     * Resuelve etiqueta de persona para eventos de Indy usando atributos revelados y fallback por identificación.
     */
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

    /**
     * Normaliza fechas ISO provenientes de Fabric.
     */
    private LocalDateTime parseFabricDate(String createdAtRaw) {
        if (createdAtRaw == null || createdAtRaw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(createdAtRaw).atZone(UI_ZONE).toLocalDateTime();
        } catch (Exception ignored) {
            // Se intenta parseo local.
        }
        try {
            return LocalDateTime.parse(createdAtRaw);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Evalúa rango temporal incluyente por día para bloques trazables.
     */
    private boolean isInDateRange(LocalDateTime value, LocalDate from, LocalDate to) {
        if (value == null) {
            return false;
        }
        LocalDate day = value.toLocalDate();
        return !day.isBefore(from) && !day.isAfter(to);
    }

    /**
     * Convierte texto en valor normalizado sin nulls.
     */
    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Devuelve el valor normalizado o una etiqueta fallback si viene vacío.
     */
    private String normalizeOrFallback(String value, String fallback) {
        String normalized = normalize(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    /**
     * Parsea {@link IdType} de forma segura para filtros de UI.
     */
    private IdType parseIdType(String value) {
        try {
            return IdType.valueOf(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Concatena advertencias preservando el contenido anterior.
     */
    private String appendWarning(String current, String incoming) {
        if (incoming == null || incoming.isBlank()) {
            return current;
        }
        if (current == null || current.isBlank()) {
            return incoming;
        }
        return current + " " + incoming;
    }

    /**
     * Extrae un mensaje raíz para mostrar advertencias no fatales en la UI.
     */
    private String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        return normalizeOrFallback(cursor.getMessage(), "Error no detallado.");
    }

    /**
     * Resultado interno de agregación para la sección blockchain del dashboard.
     */
    private record TraceAggregation(
            String traceIdType,
            String traceIdNumber,
            boolean allSelected,
            boolean lookupRequested,
            String personLabel,
            String warningMessage,
            long fabricBlocks,
            long indyBlocks,
            List<BlockchainTraceBlock> blocks
    ) {
        private static TraceAggregation empty(String traceIdType, String traceIdNumber) {
            return new TraceAggregation(traceIdType, traceIdNumber, false, false, "", null, 0L, 0L, List.of());
        }

        private static TraceAggregation withWarning(String traceIdType, String traceIdNumber, String warning) {
            return new TraceAggregation(traceIdType, traceIdNumber, false, true, "", warning, 0L, 0L, List.of());
        }
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
