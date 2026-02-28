package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.dto.DocumentUploadForm;
import co.edu.unbosque.ccdigital.dto.PersonCreateForm;
import co.edu.unbosque.ccdigital.dto.SyncPersonForm;
import co.edu.unbosque.ccdigital.entity.IdType;
import co.edu.unbosque.ccdigital.entity.Person;
import co.edu.unbosque.ccdigital.entity.PersonDocument;
import co.edu.unbosque.ccdigital.service.AdminReportPdfService;
import co.edu.unbosque.ccdigital.service.AdminReportService;
import co.edu.unbosque.ccdigital.service.BlockchainTraceDetailService;
import co.edu.unbosque.ccdigital.service.ExternalToolsService;
import co.edu.unbosque.ccdigital.service.PersonDocumentService;
import co.edu.unbosque.ccdigital.service.PersonService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Controlador web del módulo administrativo (Gobierno) de CCDigital.
 *
 * <p>
 * Expone endpoints bajo el prefijo {@code /admin} para la operación del panel administrativo,
 * incluyendo gestión de personas, gestión documental y ejecución de procesos de sincronización
 * con herramientas externas.
 * </p>
 *
 * <p>
 * La lógica de negocio se delega a los servicios; este controlador se encarga de enrutar peticiones,
 * poblar el {@link Model} y retornar vistas o redirecciones.
 * </p>
 *
 * @since 3.0
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final PersonService personService;
    private final PersonDocumentService personDocumentService;
    private final ExternalToolsService externalToolsService;
    private final AdminReportService adminReportService;
    private final AdminReportPdfService adminReportPdfService;
    private final BlockchainTraceDetailService blockchainTraceDetailService;

    /**
     * Constructor del controlador administrativo.
     *
     * @param personService servicio para operaciones sobre {@link Person}
     * @param personDocumentService servicio para operaciones sobre {@link PersonDocument}
     * @param externalToolsService servicio de ejecución de herramientas externas
     * @param adminReportService servicio de consolidación de trazabilidad administrativa
     * @param adminReportPdfService servicio de exportación PDF del dashboard de reportes
     * @param blockchainTraceDetailService servicio de lectura de detalle técnico por referencia blockchain
     */
    public AdminController(PersonService personService,
                           PersonDocumentService personDocumentService,
                           ExternalToolsService externalToolsService,
                           AdminReportService adminReportService,
                           AdminReportPdfService adminReportPdfService,
                           BlockchainTraceDetailService blockchainTraceDetailService) {
        this.personService = personService;
        this.personDocumentService = personDocumentService;
        this.externalToolsService = externalToolsService;
        this.adminReportService = adminReportService;
        this.adminReportPdfService = adminReportPdfService;
        this.blockchainTraceDetailService = blockchainTraceDetailService;
    }

    /**
     * Retorna la vista principal del dashboard administrativo.
     *
     * <p>Rutas soportadas:</p>
     * <ul>
     *   <li>{@code GET /admin}</li>
     *   <li>{@code GET /admin/}</li>
     *   <li>{@code GET /admin/dashboard}</li>
     * </ul>
     *
     * @return nombre de la vista del dashboard
     */
    @GetMapping({"", "/", "/dashboard"})
    public String dashboard() {
        return "admin/dashboard";
    }

    /**
     * Muestra el dashboard de trazabilidad (Admin > Reportes) con filtros por rango y granularidad.
     *
     * <p>Si no se envía rango temporal, se calcula sobre los últimos 30 días.</p>
     *
     * @param from fecha inicial (incluyente), opcional
     * @param to fecha final (incluyente), opcional
     * @param period granularidad de tendencia (DAY/WEEK/MONTH), opcional
     * @param traceIdType tipo de identificación para trazabilidad blockchain (opcional)
     * @param traceIdNumber número de identificación para trazabilidad blockchain (opcional)
     * @param traceAll si es true, habilita consulta de trazabilidad global sin identificación
     * @param view vista activa del dashboard de reportes (analytics/blockchain), opcional
     * @param model modelo de Spring MVC
     * @return vista de reportes administrativos
     */
    @GetMapping("/reports")
    public String reports(@RequestParam(value = "from", required = false)
                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(value = "to", required = false)
                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          @RequestParam(value = "period", required = false, defaultValue = "DAY") String period,
                          @RequestParam(value = "traceIdType", required = false) String traceIdType,
                          @RequestParam(value = "traceIdNumber", required = false) String traceIdNumber,
                          @RequestParam(value = "traceAll", required = false, defaultValue = "false") boolean traceAll,
                          @RequestParam(value = "view", required = false, defaultValue = "analytics") String view,
                          Model model) {
        AdminReportService.DashboardReport report =
                adminReportService.buildDashboard(from, to, period, traceIdType, traceIdNumber, traceAll);
        String reportView = "blockchain".equalsIgnoreCase(view) ? "blockchain" : "analytics";
        model.addAttribute("report", report);
        model.addAttribute("reportView", reportView);
        model.addAttribute("periodOptions", AdminReportService.TrendPeriod.values());
        model.addAttribute("idTypeOptions", IdType.values());
        return "admin/reports";
    }

    /**
     * Resuelve el detalle técnico completo de una referencia blockchain del dashboard Admin.
     *
     * <p>El botón "Ver bloque completo" envía la red y la referencia visible en la tarjeta.
     * Para Fabric también se envían tipo/número de identificación para resolver el bloque real
     * asociado al docId.</p>
     *
     * @param network red seleccionada (Fabric/Indy)
     * @param reference referencia funcional (docId o pres_ex_id)
     * @param idType tipo de identificación (solo Fabric)
     * @param idNumber número de identificación (solo Fabric)
     * @return payload JSON con detalle técnico, o error de validación
     */
    @GetMapping("/reports/block-detail")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> reportBlockDetail(
            @RequestParam("network") String network,
            @RequestParam("reference") String reference,
            @RequestParam(value = "idType", required = false) String idType,
            @RequestParam(value = "idNumber", required = false) String idNumber
    ) {
        try {
            Map<String, Object> payload = blockchainTraceDetailService.readDetail(network, reference, idType, idNumber);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(payload);
        } catch (IllegalArgumentException ex) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", ex.getMessage());
            error.put("network", network);
            error.put("reference", reference);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .cacheControl(CacheControl.noStore())
                    .body(error);
        } catch (Exception ex) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "No fue posible consultar el detalle del bloque en este momento.");
            error.put("detail", ex.getMessage());
            error.put("network", network);
            error.put("reference", reference);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .cacheControl(CacheControl.noStore())
                    .body(error);
        }
    }

    /**
     * Exporta en PDF el reporte de trazabilidad administrativo con los filtros actuales.
     *
     * @param from fecha inicial (incluyente), opcional
     * @param to fecha final (incluyente), opcional
     * @param period granularidad de tendencia (DAY/WEEK/MONTH), opcional
     * @return archivo PDF en modo descarga
     */
    @GetMapping("/reports/pdf")
    public ResponseEntity<byte[]> reportsPdf(@RequestParam(value = "from", required = false)
                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(value = "to", required = false)
                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                             @RequestParam(value = "period", required = false, defaultValue = "DAY") String period) {
        AdminReportService.DashboardReport report = adminReportService.buildDashboard(from, to, period);
        byte[] pdf = adminReportPdfService.generateReportPdf(report);

        String fromLabel = report.getFromDate().format(DateTimeFormatter.BASIC_ISO_DATE);
        String toLabel = report.getToDate().format(DateTimeFormatter.BASIC_ISO_DATE);
        String filename = "ccdigital-reporte-trazabilidad-" + fromLabel + "-" + toLabel + ".pdf";
        MediaType pdfMediaType = Objects.requireNonNull(MediaType.APPLICATION_PDF);

        return ResponseEntity.ok()
                .contentType(pdfMediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdf);
    }

    /**
     * Muestra la vista de listado de personas y carga los elementos necesarios para el formulario de creación.
     *
     * <p>Agrega al modelo:</p>
     * <ul>
     *   <li>{@code people}: listado de personas existentes</li>
     *   <li>{@code form}: formulario vacío {@link PersonCreateForm}</li>
     *   <li>{@code idTypes}: valores de {@link IdType}</li>
     * </ul>
     *
     * @param model modelo de Spring MVC
     * @return nombre de la vista de personas
     */
    @GetMapping("/persons")
    public String persons(Model model) {
        model.addAttribute("people", personService.findAll());
        model.addAttribute("form", new PersonCreateForm());
        model.addAttribute("idTypes", IdType.values());
        return "admin/persons";
    }

    /**
     * Muestra el formulario dedicado para crear una nueva persona.
     *
     * @param model modelo de Spring MVC
     * @return nombre de la vista del formulario de persona
     */
    @GetMapping("/persons/new")
    public String newPerson(Model model) {
        model.addAttribute("form", new PersonCreateForm());
        model.addAttribute("idTypes", IdType.values());
        return "admin/person_form";
    }

    /**
     * Crea una nueva persona a partir de los datos capturados en el formulario.
     *
     * <p>
     * La creación delega a {@link PersonService#createPersonAndFolder(Person)}.
     * </p>
     *
     * @param form datos capturados desde el formulario de creación
     * @return redirección al detalle de la persona creada
     */
    @PostMapping("/persons")
    public String createPerson(@ModelAttribute("form") PersonCreateForm form) {
        Person p = new Person();
        p.setIdType(form.getIdType());
        p.setIdNumber(form.getIdNumber());
        p.setFirstName(form.getFirstName());
        p.setLastName(form.getLastName());
        p.setEmail(form.getEmail());
        p.setPhone(form.getPhone());
        p.setBirthdate(form.getBirthdate());

        Person saved = personService.createPersonAndFolder(p);
        return "redirect:/admin/persons/" + saved.getId();
    }

    /**
     * Muestra el detalle de una persona y su listado de documentos asociados.
     *
     * @param id identificador interno de la persona
     * @param model modelo de Spring MVC
     * @return nombre de la vista de detalle de persona
     */
    @GetMapping("/persons/{id}")
    public String personDetail(@PathVariable Long id, Model model) {
        Person person = personService.findById(id).orElseThrow();
        List<PersonDocument> docs = personDocumentService.listByPerson(id);

        model.addAttribute("person", person);
        model.addAttribute("docs", docs);
        return "admin/person-detail";
    }

    /**
     * Carga (upload) un documento para una persona específica.
     *
     * <p>
     * Recibe metadatos mediante {@link DocumentUploadForm} y el archivo físico como {@link MultipartFile}.
     * La persistencia y el almacenamiento se delegan a {@link PersonDocumentService}.
     * </p>
     *
     * @param id identificador de la persona a la cual se asociará el documento
     * @param uploadForm formulario con metadatos del documento
     * @param file archivo cargado desde la UI
     * @return redirección al detalle de la persona
     */
    @PostMapping("/persons/{id}/upload")
    public String uploadDoc(@PathVariable("id") Long id,
                            @ModelAttribute("uploadForm") DocumentUploadForm uploadForm,
                            @RequestParam("file") MultipartFile file) {

        personDocumentService.uploadForPerson(
                id,
                uploadForm.getDocumentId(),
                uploadForm.getStatus(),
                uploadForm.getIssueDate(),
                uploadForm.getExpiryDate(),
                file
        );

        return "redirect:/admin/persons/" + id;
    }

    /**
     * Muestra la página de sincronización del módulo administrativo.
     *
     * @param model modelo de Spring MVC
     * @return nombre de la vista de sincronización
     */
    @GetMapping("/sync")
    public String syncPage(Model model) {
        model.addAttribute("personForm", new SyncPersonForm());
        model.addAttribute("idTypes", IdType.values());
        model.addAttribute("result", null);
        return "admin/sync";
    }

    /**
     * Ejecuta la sincronización completa hacia Hyperledger Fabric.
     *
     * @param model modelo de Spring MVC
     * @return nombre de la vista de sincronización
     */
    @PostMapping("/sync/fabric/all")
    public String fabricAll(Model model) {
        ExternalToolsService.ExecResult res = externalToolsService.runFabricSyncAll();

        model.addAttribute("result", res);
        model.addAttribute("personForm", new SyncPersonForm());
        model.addAttribute("idTypes", IdType.values());
        return "admin/sync";
    }

    /**
     * Ejecuta la sincronización hacia Hyperledger Fabric para una persona específica.
     *
     * @param form formulario con {@link IdType} y número de identificación
     * @param model modelo de Spring MVC
     * @return nombre de la vista de sincronización con el resultado
     */
    @PostMapping("/sync/fabric/person")
    public String fabricPerson(@ModelAttribute("personForm") SyncPersonForm form, Model model) {
        ExternalToolsService.ExecResult res =
                externalToolsService.runFabricSyncPerson(form.getIdType().name(), form.getIdNumber());

        model.addAttribute("result", res);
        model.addAttribute("personForm", form);
        model.addAttribute("idTypes", IdType.values());
        return "admin/sync";
    }

    /**
     * Ejecuta la emisión de credenciales Indy a partir de la información disponible en el sistema.
     *
     * @param model modelo de Spring MVC
     * @return nombre de la vista de sincronización con el resultado
     */
    @PostMapping("/sync/indy/issue")
    public String indyIssue(Model model) {
        ExternalToolsService.ExecResult res = externalToolsService.runIndyIssueFromDb();

        model.addAttribute("result", res);
        model.addAttribute("personForm", new SyncPersonForm());
        model.addAttribute("idTypes", IdType.values());
        return "admin/sync";
    }
}
