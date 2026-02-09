package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.dto.DocumentUploadForm;
import co.edu.unbosque.ccdigital.dto.PersonCreateForm;
import co.edu.unbosque.ccdigital.dto.SyncPersonForm;
import co.edu.unbosque.ccdigital.entity.IdType;
import co.edu.unbosque.ccdigital.entity.Person;
import co.edu.unbosque.ccdigital.entity.PersonDocument;
import co.edu.unbosque.ccdigital.service.DocumentDefinitionService;
import co.edu.unbosque.ccdigital.service.ExternalToolsService;
import co.edu.unbosque.ccdigital.service.PersonDocumentService;
import co.edu.unbosque.ccdigital.service.PersonService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Controlador web del módulo administrativo (Gobierno) de CCDigital.
 *
 * <p>Expone endpoints bajo el prefijo {@code /admin} para:</p>
 * <ul>
 *   <li>Visualizar el dashboard administrativo.</li>
 *   <li>Gestionar personas (listar, crear, ver detalle).</li>
 *   <li>Subir documentos asociados a una persona.</li>
 *   <li>Ejecutar acciones de sincronización con herramientas externas (Fabric/Indy).</li>
 * </ul>
 *
 * <p> La lógica de negocio se delega a servicios, el controlador se encarga
 * principalmente de enrutar peticiones HTTP, armar el {@link Model} y redireccionar
 * a las vistas correspondientes.</p>
 *
 * @author Danniel
 * @author Yeison 
 * @since 3.0
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    /**
     * Servicio de gestión de personas (CRUD y utilidades relacionadas, como creación de carpeta).
     */
    private final PersonService personService;

    /**
     * Servicio de gestión de documentos asociados a personas.
     */
    private final PersonDocumentService personDocumentService;

    /**
     * Servicio de definiciones de documentos (catálogo, reglas o metadatos de documentos).
     */
    private final DocumentDefinitionService documentDefinitionService;

    /**
     * Servicio que encapsula la ejecución de herramientas externas como son los scripts de Fabric e Indy.
     */
    private final ExternalToolsService externalToolsService;

    /**
     * Construye el controlador administrativo inyectando las dependencias requeridas.
     *
     * @param personService servicio para operaciones sobre {@link Person}
     * @param personDocumentService servicio para operaciones sobre {@link PersonDocument}
     * @param documentDefinitionService servicio de definiciones/catálogos de documentos
     * @param externalToolsService servicio de ejecución de herramientas externas Fabric y Indy
     */
    public AdminController(PersonService personService,
                           PersonDocumentService personDocumentService,
                           DocumentDefinitionService documentDefinitionService,
                           ExternalToolsService externalToolsService) {
        this.personService = personService;
        this.personDocumentService = personDocumentService;
        this.documentDefinitionService = documentDefinitionService;
        this.externalToolsService = externalToolsService;
    }

    /**
     * Vista de dashboard del módulo administrativo.
     *
     * <p>Rutas soportadas:</p>
     * <ul>
     *   <li>{@code GET /admin/}</li>
     *   <li>{@code GET /admin/dashboard}</li>
     * </ul>
     *
     * @return nombre de la vista del dashboard administrativo
     */
    @GetMapping({"", "/", "/dashboard"})
    public String dashboard() {
        return "admin/dashboard";
    }

    /**
     * Muestra la vista de listado de personas y carga los elementos requeridos por el formulario de creación.
     *
     * <p>Agrega al {@link Model}:</p>
     * <ul>
     *   <li>{@code people}: listado de personas existentes.</li>
     *   <li>{@code form}: formulario vacío {@link PersonCreateForm} para la creación.</li>
     *   <li>{@code idTypes}: valores disponibles de {@link IdType} para el selector en la UI.</li>
     * </ul>
     *
     * @param model modelo de Spring MVC usado para enviar atributos a la vista
     * @return nombre de la vista de listado de personas
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
     * <p>Agrega al {@link Model}:</p>
     * <ul>
     *   <li>{@code form}: instancia vacía de {@link PersonCreateForm}.</li>
     *   <li>{@code idTypes}: valores disponibles de {@link IdType}.</li>
     * </ul>
     *
     * @param model modelo de Spring MVC usado para enviar atributos a la vista
     * @return nombre de la vista del formulario de persona
     */
    @GetMapping("/persons/new")
    public String newPerson(Model model) {
        model.addAttribute("form", new PersonCreateForm());
        model.addAttribute("idTypes", IdType.values());
        return "admin/person_form";
    }

    /**
     * Crea una nueva persona con la información recibida desde la UI.
     *
     * <p>La creación delega al servicio {@link PersonService}, el cual puede realizar tareas adicionales
     * como crear una carpeta asociada a la persona (por ejemplo, para almacenamiento de archivos).</p>
     *
     * @param form datos capturados desde el formulario de creación
     * @return redirección al detalle de la persona recién creada
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
     * <p>Agrega al {@link Model}:</p>
     * <ul>
     *   <li>{@code person}: entidad {@link Person} consultada por id.</li>
     *   <li>{@code docs}: lista de {@link PersonDocument} asociados a la persona.</li>
     * </ul>
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
     * <p>Este endpoint recibe metadatos del documento mediante {@link DocumentUploadForm}
     * y el archivo físico como {@link MultipartFile}. La persistencia y almacenamiento
     * se delegan a {@link PersonDocumentService}.</p>
     *
     * @param id de la persona a la cual se asociará el documento
     * @param uploadForm formulario con metadatos
     * @param file archivo cargado desde el formulario HTML
     * @return redirección al detalle de la persona: {@code /admin/persons/{id}}
     */
    @PostMapping("/persons/{id}/upload")
    public String uploadDoc(@PathVariable Long id,
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
     * Muestra la página de sincronización (Sync) del módulo administrativo.
     *
     * <p>Agrega al {@link Model}:</p>
     * <ul>
     *   <li>{@code personForm}: formulario vacío {@link SyncPersonForm}.</li>
     *   <li>{@code idTypes}: valores de {@link IdType}.</li>
     *   <li>{@code result}: resultado nulo evitando valores nulos.</li>
     * </ul>
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
     * Ejecuta la sincronización completa hacia Hyperledger Fabric (todas las personas/documentos).
     *
     * <p>Invoca {@link ExternalToolsService#runFabricSyncAll()} y retorna a la misma vista de sync
     * con el resultado de ejecución.</p>
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
     * Ejecuta la sincronización hacia Hyperledger Fabric para una persona específica,
     * identificada por tipo y número de documento.
     *
     * @param form formulario con el {@link IdType} y el número de identificación
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
     * Ejecuta la emisión de credenciales en Indy a partir de la información existente en base de datos.
     *
     * <p>Este endpoint está ruteado bajo {@code POST /admin/sync/indy/issue} para evitar duplicidad
     * de prefijo {@code /admin}. Retorna a la misma vista de sincronización con el resultado.</p>
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