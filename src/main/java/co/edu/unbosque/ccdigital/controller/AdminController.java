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
 * Controlador MVC para funcionalidades administrativas.
 *
 * <p>Gestiona vistas y acciones relacionadas con:</p>
 * <ul>
 *   <li>Gestión de personas y sus documentos</li>
 *   <li>Sincronizaciones con herramientas externas</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final PersonService personService;
    private final PersonDocumentService personDocumentService;
    private final DocumentDefinitionService documentDefinitionService;
    private final ExternalToolsService externalToolsService;

    /**
     * Constructor con inyección de dependencias.
     *
     * @param personService servicio de personas
     * @param personDocumentService servicio de documentos por persona
     * @param documentDefinitionService servicio de definiciones de documentos
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
     * Vista principal del panel administrativo.
     *
     * @return nombre de la vista del dashboard
     */
    @GetMapping({"", "/", "/dashboard"})
    public String dashboard() {
        return "admin/dashboard";
    }

    /**
     * Lista las personas registradas y prepara el formulario de creación.
     *
     * @param model modelo MVC
     * @return vista de listado de personas
     */
    @GetMapping("/persons")
    public String persons(Model model) {
        model.addAttribute("people", personService.findAll());
        model.addAttribute("form", new PersonCreateForm());
        model.addAttribute("idTypes", IdType.values());
        return "admin/persons";
    }

    /**
     * Presenta el formulario de creación de persona.
     *
     * @param model modelo MVC
     * @return vista del formulario
     */
    @GetMapping("/persons/new")
    public String newPerson(Model model) {
        model.addAttribute("form", new PersonCreateForm());
        model.addAttribute("idTypes", IdType.values());
        return "admin/person_form";
    }

    /**
     * Crea una persona y prepara su carpeta de almacenamiento.
     *
     * @param form formulario de creación de persona
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
     * Visualiza el detalle de una persona, su listado de documentos y prepara el formulario de carga.
     *
     * @param id identificador de la persona
     * @param model modelo MVC
     * @return vista del detalle de la persona
     */
    @GetMapping("/persons/{id}")
    public String personDetail(@PathVariable Long id, Model model) {
        Person person = personService.findById(id).orElseThrow();
        List<PersonDocument> docs = personDocumentService.listByPerson(id);

        model.addAttribute("person", person);
        model.addAttribute("docs", docs);
        model.addAttribute("uploadForm", new DocumentUploadForm());
        model.addAttribute("allDocs", documentDefinitionService.findAll());

        return "admin/person-detail";
    }

    /**
     * Carga un documento asociado a una persona.
     *
     * @param id identificador de la persona
     * @param uploadForm formulario con metadatos del documento
     * @param file archivo a cargar
     * @return redirección al detalle de la persona
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
     * Muestra la vista de sincronización.
     *
     * @param model modelo MVC
     * @return vista de sincronización
     */
    @GetMapping("/sync")
    public String syncPage(Model model) {
        model.addAttribute("personForm", new SyncPersonForm());
        model.addAttribute("idTypes", IdType.values());
        model.addAttribute("result", null);
        return "admin/sync";
    }

    /**
     * Ejecuta la sincronización global con Fabric.
     *
     * @param model modelo MVC
     * @return vista de sincronización con el resultado de ejecución
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
     * Ejecuta la sincronización en Fabric para una persona identificada por tipo y número.
     *
     * @param form formulario con datos de identificación
     * @param model modelo MVC
     * @return vista de sincronización con el resultado de ejecución
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
     * Ejecuta la emisión de credenciales en Indy usando la información de base de datos.
     *
     * @param model modelo MVC
     * @return vista de sincronización con el resultado de ejecución
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
