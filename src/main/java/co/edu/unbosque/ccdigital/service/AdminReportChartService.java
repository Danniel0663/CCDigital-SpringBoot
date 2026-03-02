package co.edu.unbosque.ccdigital.service;

import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.knowm.xchart.PieChart;
import org.knowm.xchart.PieChartBuilder;
import org.knowm.xchart.internal.chartpart.Chart;
import org.knowm.xchart.style.Styler;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * Genera graficas server-side para incrustarlas en el PDF de reportes.
 *
 * <p>El PDF se renderiza con OpenHTMLtoPDF (sin JavaScript), por eso las graficas
 * se producen en backend como imagen PNG embebida en Base64.</p>
 */
@Service
public class AdminReportChartService {

    /**
     * Paquete de imagenes para la plantilla del PDF.
     */
    public record ChartAssets(
            String trendRequestsChart,
            String trendDocumentsChart,
            String successDistributionChart,
            String topDocumentsChart,
            String topIssuersChart
    ) {
    }

    /**
     * Construye todas las graficas del reporte para exportacion.
     *
     * @param report dataset consolidado de Admin > Reportes
     * @return rutas data-uri de imagenes PNG listas para <img src="...">
     */
    public ChartAssets buildCharts(AdminReportService.DashboardReport report) {
        Objects.requireNonNull(report, "report");
        return new ChartAssets(
                buildTrendRequestsChart(report),
                buildTrendDocumentsChart(report),
                buildSuccessDistributionChart(report),
                buildTopDocumentsChart(report),
                buildTopIssuersChart(report)
        );
    }

    /**
     * Grafica tendencia temporal de solicitudes (total/exitosas/no exitosas).
     */
    private String buildTrendRequestsChart(AdminReportService.DashboardReport report) {
        List<String> labels = new ArrayList<>();
        List<Long> total = new ArrayList<>();
        List<Long> success = new ArrayList<>();
        List<Long> unsuccess = new ArrayList<>();
        for (AdminReportService.TrendRow row : report.getTrendRows()) {
            labels.add(row.getPeriodLabel());
            total.add(row.getTotalRequests());
            success.add(row.getSuccessfulRequests());
            unsuccess.add(row.getUnsuccessfulRequests());
        }
        if (labels.isEmpty()) {
            labels.add("Sin datos");
            total.add(0L);
            success.add(0L);
            unsuccess.add(0L);
        }
        List<String> chartLabels = labels.stream().map(this::toCompactPeriodLabel).toList();

        CategoryChart chart = new CategoryChartBuilder()
                .width(960)
                .height(380)
                .title("Tendencia de solicitudes")
                .xAxisTitle("Periodo")
                .yAxisTitle("Cantidad")
                .build();
        chart.getStyler().setChartBackgroundColor(java.awt.Color.WHITE);
        chart.getStyler().setPlotGridLinesVisible(true);
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setXAxisLabelRotation(70);
        chart.getStyler().setAxisTickLabelsFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
        chart.getStyler().setChartTitleFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        chart.getStyler().setPlotMargin(6);
        chart.getStyler().setDefaultSeriesRenderStyle(org.knowm.xchart.CategorySeries.CategorySeriesRenderStyle.Line);
        chart.addSeries("Solicitudes", chartLabels, total);
        chart.addSeries("Exitosas", chartLabels, success);
        chart.addSeries("No exitosas", chartLabels, unsuccess);
        return toDataUri(chart);
    }

    /**
     * Grafica tendencia temporal de documentos consultados vs depositados.
     */
    private String buildTrendDocumentsChart(AdminReportService.DashboardReport report) {
        List<String> labels = new ArrayList<>();
        List<Long> consulted = new ArrayList<>();
        List<Long> deposited = new ArrayList<>();
        for (AdminReportService.TrendRow row : report.getTrendRows()) {
            labels.add(row.getPeriodLabel());
            consulted.add(row.getDocumentsConsulted());
            deposited.add(row.getDocumentsDeposited());
        }
        if (labels.isEmpty()) {
            labels.add("Sin datos");
            consulted.add(0L);
            deposited.add(0L);
        }
        List<String> chartLabels = labels.stream().map(this::toCompactPeriodLabel).toList();

        CategoryChart chart = new CategoryChartBuilder()
                .width(960)
                .height(380)
                .title("Documentos consultados y depositados")
                .xAxisTitle("Periodo")
                .yAxisTitle("Cantidad")
                .build();
        chart.getStyler().setChartBackgroundColor(java.awt.Color.WHITE);
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setAvailableSpaceFill(0.35);
        chart.getStyler().setOverlapped(false);
        chart.getStyler().setXAxisLabelRotation(70);
        chart.getStyler().setAxisTickLabelsFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
        chart.getStyler().setChartTitleFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        chart.getStyler().setPlotMargin(6);
        chart.addSeries("Consultados", chartLabels, consulted);
        chart.addSeries("Depositados", chartLabels, deposited);
        return toDataUri(chart);
    }

    /**
     * Grafica de distribucion global exitosas vs no exitosas.
     */
    private String buildSuccessDistributionChart(AdminReportService.DashboardReport report) {
        long success = report.getSuccessfulRequests();
        long unsuccess = report.getUnsuccessfulRequests();

        PieChart chart = new PieChartBuilder()
                .width(640)
                .height(420)
                .title("Distribucion de solicitudes")
                .build();
        chart.getStyler().setLegendPosition(Styler.LegendPosition.OutsideE);
        chart.getStyler().setChartBackgroundColor(java.awt.Color.WHITE);
        chart.getStyler().setDecimalPattern("#,###");

        if (success <= 0L && unsuccess <= 0L) {
            chart.addSeries("Sin datos", 1);
        } else {
            chart.addSeries("Exitosas", Math.max(success, 0L));
            chart.addSeries("No exitosas", Math.max(unsuccess, 0L));
        }
        return toDataUri(chart);
    }

    /**
     * Grafica Top de documentos mas consultados.
     */
    private String buildTopDocumentsChart(AdminReportService.DashboardReport report) {
        return buildTopChart(report.getTopDocuments(), "Top documentos consultados");
    }

    /**
     * Grafica Top de emisores mas solicitados.
     */
    private String buildTopIssuersChart(AdminReportService.DashboardReport report) {
        return buildTopChart(report.getTopIssuers(), "Top emisores solicitados");
    }

    /**
     * Construye grafica de barras para una lista Top.
     */
    private String buildTopChart(List<AdminReportService.TopRow> rows, String title) {
        List<String> labels = new ArrayList<>();
        List<Long> values = new ArrayList<>();
        for (AdminReportService.TopRow row : rows) {
            labels.add(cropLabel(row.getLabel(), 28));
            values.add(row.getTotal());
            if (labels.size() >= 8) {
                break;
            }
        }
        if (labels.isEmpty()) {
            labels.add("Sin datos");
            values.add(0L);
        }

        CategoryChart chart = new CategoryChartBuilder()
                .width(620)
                .height(380)
                .title(title)
                .xAxisTitle("Elemento")
                .yAxisTitle("Total")
                .build();
        chart.getStyler().setChartBackgroundColor(java.awt.Color.WHITE);
        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setAvailableSpaceFill(0.45);
        chart.getStyler().setXAxisLabelRotation(55);
        chart.getStyler().setAxisTickLabelsFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
        chart.addSeries("Total", labels, values);
        return toDataUri(chart);
    }

    /**
     * Convierte la grafica a data URI PNG para incrustacion directa en XHTML.
     */
    private String toDataUri(Chart<?, ?> chart) {
        try {
            BufferedImage image = BitmapEncoder.getBufferedImage(chart);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            String encoded = Base64.getEncoder().encodeToString(out.toByteArray());
            return "data:image/png;base64," + encoded;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Acorta etiquetas para evitar desbordes visuales en ejes de graficas.
     */
    private String cropLabel(String text, int maxLen) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            return "Sin etiqueta";
        }
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, Math.max(1, maxLen - 1)) + "...";
    }

    /**
     * Compacta etiquetas de periodo para evitar recortes y solapamientos en el eje X.
     */
    private String toCompactPeriodLabel(String label) {
        String value = label == null ? "" : label.trim();
        if (value.matches("\\d{2}/\\d{2}/\\d{4}")) {
            return value.substring(0, 5);
        }
        if (value.matches("\\d{2}/\\d{2}/\\d{4}\\s*-\\s*\\d{2}/\\d{2}/\\d{4}")) {
            return value.substring(0, 5) + "-" + value.substring(13, 18);
        }
        return cropLabel(value, 16);
    }

}
