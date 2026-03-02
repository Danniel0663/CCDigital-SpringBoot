package co.edu.unbosque.ccdigital.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Servicio de exportación PDF para el dashboard Admin > Reportes.
 *
 * <p>Transforma una vista HTML (Thymeleaf) en un documento PDF listo para descarga,
 * manteniendo un formato estable y profesional para uso administrativo.</p>
 *
 * @since 3.0
 */
@Service
public class AdminReportPdfService {

    private static final DateTimeFormatter PRINT_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final TemplateEngine templateEngine;
    private final AdminReportChartService adminReportChartService;

    /**
     * Constructor del exportador PDF.
     *
     * @param templateEngine motor de plantillas Thymeleaf de la aplicación
     */
    public AdminReportPdfService(TemplateEngine templateEngine,
                                 AdminReportChartService adminReportChartService) {
        this.templateEngine = templateEngine;
        this.adminReportChartService = adminReportChartService;
    }

    /**
     * Genera un PDF del reporte de trazabilidad administrativo.
     *
     * @param report dataset consolidado de reportes
     * @return bytes del PDF generado
     */
    public byte[] generateReportPdf(AdminReportService.DashboardReport report) {
        AdminReportChartService.ChartAssets charts = adminReportChartService.buildCharts(report);

        long totalRequests = report.getTotalRequests();
        long successfulRequests = report.getSuccessfulRequests();
        long unsuccessfulRequests = report.getUnsuccessfulRequests();
        double successRate = totalRequests > 0 ? (successfulRequests * 100.0d / totalRequests) : 0.0d;
        double unsuccessRate = totalRequests > 0 ? (unsuccessfulRequests * 100.0d / totalRequests) : 0.0d;

        Context context = new Context();
        context.setVariable("report", report);
        context.setVariable("generatedAt", PRINT_DATE_TIME.format(LocalDateTime.now()));
        context.setVariable("successRate", formatPercent(successRate));
        context.setVariable("unsuccessRate", formatPercent(unsuccessRate));
        context.setVariable("executiveInsights", buildExecutiveInsights(report, successRate));
        context.setVariable("trendRequestsChart", charts.trendRequestsChart());
        context.setVariable("trendDocumentsChart", charts.trendDocumentsChart());
        context.setVariable("successDistributionChart", charts.successDistributionChart());
        context.setVariable("topDocumentsChart", charts.topDocumentsChart());
        context.setVariable("topIssuersChart", charts.topIssuersChart());

        // Se renderiza primero a HTML y luego se convierte a PDF para conservar layout consistente.
        String html = templateEngine.process("admin/reports-pdf", context);
        // OpenHTMLtoPDF usa parser XML: se elimina BOM UTF-8 si aparece al inicio.
        if (html != null && !html.isEmpty() && html.charAt(0) == '\uFEFF') {
            html = html.substring(1);
        }
        // OpenHTMLtoPDF interpreta XHTML/XML estricto: se normaliza &nbsp; a su entidad numérica.
        if (html != null && html.contains("&nbsp;")) {
            html = html.replace("&nbsp;", "&#160;");
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible generar el PDF de reportes.", e);
        }
    }

    /**
     * Formatea porcentaje para visualización ejecutiva en PDF.
     */
    private String formatPercent(double value) {
        return String.format(java.util.Locale.US, "%.1f%%", value);
    }

    /**
     * Construye bullets de lectura ejecutiva para facilitar interpretación del reporte.
     */
    private java.util.List<String> buildExecutiveInsights(AdminReportService.DashboardReport report,
                                                          double successRate) {
        java.util.List<String> insights = new java.util.ArrayList<>();

        insights.add("Se registraron " + report.getTotalRequests() + " solicitudes en el periodo analizado.");
        insights.add("La tasa de éxito del flujo de solicitudes fue de " + formatPercent(successRate) + ".");

        if (!report.getTopDocuments().isEmpty()) {
            AdminReportService.TopRow topDoc = report.getTopDocuments().get(0);
            insights.add("El documento más consultado fue '" + topDoc.getLabel() + "' con " + topDoc.getTotal() + " consultas.");
        } else {
            insights.add("No se encontraron consultas documentales en el rango seleccionado.");
        }

        if (!report.getTopIssuers().isEmpty()) {
            AdminReportService.TopRow topIssuer = report.getTopIssuers().get(0);
            insights.add("El emisor con mayor actividad fue '" + topIssuer.getLabel() + "' con " + topIssuer.getTotal() + " solicitudes.");
        } else {
            insights.add("No hubo actividad suficiente para consolidar ranking de emisores.");
        }

        return insights;
    }
}
