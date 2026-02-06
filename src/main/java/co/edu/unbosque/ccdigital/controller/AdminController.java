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

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final PersonService personService;
    private final PersonDocumentService personDocumentService;
    private final DocumentDefinitionService documentDefinitionService;
    private final ExternalToolsService externalToolsService;

    public AdminController(PersonService personService,
                           PersonDocumentService personDocumentService,
                           DocumentDefinitionService documentDefinitionService,
                           ExternalToolsService externalToolsService) {
        this.personService = personService;
        this.personDocumentService = personDocumentService;
        this.documentDefinitionService = documentDefinitionService;
        this.externalToolsService = externalToolsService;
    }

    @GetMapping({"", "/", "/dashboard"})
    public String dashboard() {
        return "admin/dashboard";
    }

    // ===== PERSONAS =====

    @GetMapping("/persons")
    public String persons(Model model) {
        model.addAttribute("people", personService.findAll());
        model.addAttribute("form", new PersonCreateForm());
        model.addAttribute("idTypes", IdType.values());
        return "admin/persons";
    }

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

    @GetMapping("/persons/{id}")
    public String personDetail(@PathVariable Long id, Model model) {
        Person person = personService.findById(id).orElseThrow();
        List<PersonDocument> docs = personDocumentService.listByPerson(id);

        model.addAttribute("person", person);
        model.addAttribute("docs", docs);

        model.addAttribute("uploadForm", new DocumentUploadForm());
        model.addAttribute("definitions", documentDefinitionService.findAll());
        return "admin/person-detail";
    }

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

    // ===== SYNC =====

    @GetMapping("/sync")
    public String syncPage(Model model) {
        model.addAttribute("personForm", new SyncPersonForm());
        model.addAttribute("idTypes", IdType.values());
        model.addAttribute("result", null); // opcional, pero evita null checks raros
        return "admin/sync";
    }

    @PostMapping("/sync/fabric/all")
    public String fabricAll(Model model) {
        ExternalToolsService.ExecResult res = externalToolsService.runFabricSyncAll();

        model.addAttribute("result", res);
        model.addAttribute("personForm", new SyncPersonForm());
        model.addAttribute("idTypes", IdType.values());
        return "admin/sync";
    }

    @PostMapping("/sync/fabric/person")
    public String fabricPerson(@ModelAttribute("personForm") SyncPersonForm form, Model model) {
        ExternalToolsService.ExecResult res =
                externalToolsService.runFabricSyncPerson(form.getIdType().name(), form.getIdNumber());

        model.addAttribute("result", res);
        model.addAttribute("personForm", form);
        model.addAttribute("idTypes", IdType.values());
        return "admin/sync";
    }

    // ✅ ESTE ERA EL QUE FALTABA / ESTABA MAL RUTEADO
    @PostMapping("/sync/indy/issue")
    public String indyIssue(Model model) {
        ExternalToolsService.ExecResult res = externalToolsService.runIndyIssueFromDb();

        model.addAttribute("result", res);
        model.addAttribute("personForm", new SyncPersonForm());
        model.addAttribute("idTypes", IdType.values());
        return "admin/sync";
    }

    // (Opcional) Si tú tienes una vista admin/tools aparte, déjalo así PERO SIN DOBLE /admin:
    // @PostMapping("/tools/indy/issue")
    // public String runIndyIssueTools(Model model) {
    //     ExternalToolsService.ExecResult res = externalToolsService.runIndyIssueFromDb();
    //     model.addAttribute("indyExitCode", res.getExitCode());
    //     model.addAttribute("indyStdout", res.getStdout());
    //     model.addAttribute("indyStderr", res.getStderr());
    //     return "admin/tools";
    // }

}
