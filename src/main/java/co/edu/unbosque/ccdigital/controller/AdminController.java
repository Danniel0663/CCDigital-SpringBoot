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
    private final DocumentDefinitionService documentDefinitionService;
    private final ExternalToolsService externalToolsService;

    /**
     * Constructor del controlador administrativo.
     *
     * @param personService servicio para operaciones sobre {@link Person}
     * @param personDocumentService servicio para operaciones sobre {@link PersonDocument}
     * @param documentDefinitionService servicio de definiciones/catálogos de documentos
     * @param externalToolsService servicio de ejecución de herramientas externas
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
