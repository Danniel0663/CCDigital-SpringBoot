package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.entity.AccessRequest;
import co.edu.unbosque.ccdigital.entity.PersonDocument;
import co.edu.unbosque.ccdigital.repository.AccessRequestRepository;
import co.edu.unbosque.ccdigital.repository.PersonDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

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

    private final AccessRequestRepository accessRequestRepository;
    private final PersonDocumentRepository personDocumentRepository;
    private final AdminReportTrendCalculator trendCalculator;
    private final AdminReportTopTableCalculator topTableCalculator;
    private final AdminBlockchainTraceAggregator blockchainTraceAggregator;

    /**
     * Constructor del servicio de reportes administrativos.
     *
     * @param accessRequestRepository repositorio de solicitudes de acceso
     * @param personDocumentRepository repositorio de documentos de persona
     * @param trendCalculator componente de cálculo de KPIs y tendencia temporal
     * @param topTableCalculator componente de cálculo de tablas Top
     * @param blockchainTraceAggregator componente de agregación blockchain (Fabric + Indy)
     */
    public AdminReportService(AccessRequestRepository accessRequestRepository,
                              PersonDocumentRepository personDocumentRepository,
                              AdminReportTrendCalculator trendCalculator,
                              AdminReportTopTableCalculator topTableCalculator,
                              AdminBlockchainTraceAggregator blockchainTraceAggregator) {
        this.accessRequestRepository = accessRequestRepository;
        this.personDocumentRepository = personDocumentRepository;
        this.trendCalculator = trendCalculator;
        this.topTableCalculator = topTableCalculator;
        this.blockchainTraceAggregator = blockchainTraceAggregator;
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
            } catch (IllegalArgumentException ignored) {
                return DAY;
            }
        }

        LocalDate bucketStart(LocalDate date) {
            if (this == WEEK) {
                return date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            }
            if (this == MONTH) {
                return date.withDayOfMonth(1);
            }
            return date;
        }

        LocalDate nextBucket(LocalDate bucketStart) {
            if (this == WEEK) {
                return bucketStart.plusWeeks(1);
            }
            if (this == MONTH) {
                return bucketStart.plusMonths(1);
            }
            return bucketStart.plusDays(1);
        }

        String formatBucketLabel(LocalDate bucketStart) {
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

        AdminReportTrendCalculator.TrendMetrics trendMetrics = trendCalculator.compute(
                allRequests,
                allPersonDocuments,
                from,
                to,
                period,
                fromStart,
                toEndExclusive
        );

        AdminReportTopTableCalculator.TopTables topTables = topTableCalculator.compute(
                trendMetrics.requestsInRange(),
                TOP_LIMIT
        );

        AdminBlockchainTraceAggregator.TraceResult trace = blockchainTraceAggregator.build(
                from,
                to,
                traceIdType,
                traceIdNumber,
                traceAllRequested
        );

        return new DashboardReport(
                from,
                to,
                period,
                trendMetrics.totalRequests(),
                trendMetrics.successfulRequests(),
                trendMetrics.unsuccessfulRequests(),
                trendMetrics.documentsConsulted(),
                trendMetrics.documentsDeposited(),
                trendMetrics.trendRows(),
                topTables.topDocuments(),
                topTables.topPeople(),
                topTables.topIssuers(),
                topTables.topErrors(),
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

}
