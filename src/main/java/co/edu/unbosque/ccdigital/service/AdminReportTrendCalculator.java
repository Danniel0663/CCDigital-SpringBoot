package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.entity.AccessRequest;
import co.edu.unbosque.ccdigital.entity.AccessRequestStatus;
import co.edu.unbosque.ccdigital.entity.PersonDocument;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calculadora de métricas base y tendencia temporal para reportes administrativos.
 *
 * <p>Separa la responsabilidad de agregación temporal para mantener
 * {@link AdminReportService} como orquestador del flujo.</p>
 */
@Component
final class AdminReportTrendCalculator {

    /**
     * Ejecuta el cálculo de KPIs base y filas de tendencia en una sola pasada lógica.
     */
    TrendMetrics compute(List<AccessRequest> allRequests,
                         List<PersonDocument> allPersonDocuments,
                         LocalDate from,
                         LocalDate to,
                         AdminReportService.TrendPeriod period,
                         LocalDateTime fromStart,
                         LocalDateTime toEndExclusive) {
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

        List<AdminReportService.TrendRow> trendRows = buckets.values().stream()
                .map(acc -> new AdminReportService.TrendRow(
                        acc.label,
                        acc.totalRequests,
                        acc.successfulRequests,
                        acc.unsuccessfulRequests,
                        acc.documentsConsulted,
                        acc.documentsDeposited
                ))
                .toList();

        return new TrendMetrics(
                requestsInRange,
                totalRequests,
                successfulRequests,
                unsuccessfulRequests,
                documentsConsulted,
                documentsDeposited,
                trendRows
        );
    }

    private void aggregateRequestTrends(List<AccessRequest> requests,
                                        Map<LocalDate, BucketAccumulator> buckets,
                                        AdminReportService.TrendPeriod period) {
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

    private void aggregateDepositTrends(List<PersonDocument> personDocuments,
                                        LocalDateTime fromStart,
                                        LocalDateTime toEndExclusive,
                                        Map<LocalDate, BucketAccumulator> buckets,
                                        AdminReportService.TrendPeriod period) {
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

    private LinkedHashMap<LocalDate, BucketAccumulator> initBuckets(LocalDate from,
                                                                     LocalDate to,
                                                                     AdminReportService.TrendPeriod period) {
        LinkedHashMap<LocalDate, BucketAccumulator> buckets = new LinkedHashMap<>();
        LocalDate cursor = period.bucketStart(from);
        LocalDate end = period.bucketStart(to);

        while (!cursor.isAfter(end)) {
            buckets.put(cursor, new BucketAccumulator(period.formatBucketLabel(cursor)));
            cursor = period.nextBucket(cursor);
        }

        return buckets;
    }

    private boolean isInDateTimeRange(LocalDateTime value, LocalDateTime fromInclusive, LocalDateTime toExclusive) {
        return value != null && !value.isBefore(fromInclusive) && value.isBefore(toExclusive);
    }

    private long safeItemsCount(AccessRequest request) {
        return (request == null || request.getItems() == null) ? 0L : request.getItems().size();
    }

    /**
     * Resultado de cálculo de tendencia y KPIs base.
     */
    record TrendMetrics(
            List<AccessRequest> requestsInRange,
            long totalRequests,
            long successfulRequests,
            long unsuccessfulRequests,
            long documentsConsulted,
            long documentsDeposited,
            List<AdminReportService.TrendRow> trendRows
    ) {
    }

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
