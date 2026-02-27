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

    /**
     * Constructor del exportador PDF.
     *
     * @param templateEngine motor de plantillas Thymeleaf de la aplicación
     */
    public AdminReportPdfService(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /**
     * Genera un PDF del reporte de trazabilidad administrativo.
     *
     * @param report dataset consolidado de reportes
     * @return bytes del PDF generado
     */
    public byte[] generateReportPdf(AdminReportService.DashboardReport report) {
        Context context = new Context();
        context.setVariable("report", report);
        context.setVariable("generatedAt", PRINT_DATE_TIME.format(LocalDateTime.now()));

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
}
