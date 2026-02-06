package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.dto.IssuerSearchForm;
import co.edu.unbosque.ccdigital.dto.IssuerUploadForm;
import co.edu.unbosque.ccdigital.entity.IdType;
import co.edu.unbosque.ccdigital.entity.Person;
import co.edu.unbosque.ccdigital.service.DocumentDefinitionService;
import co.edu.unbosque.ccdigital.service.IssuingEntityService;
import co.edu.unbosque.ccdigital.service.PersonDocumentService;
import co.edu.unbosque.ccdigital.service.PersonService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;

@Controller
@RequestMapping("/issuer")
public class IssuerController {

    private final IssuingEntityService issuingEntityService;
    private final PersonService personService;
    private final PersonDocumentService personDocumentService;
    private final DocumentDefinitionService documentDefinitionService;

    public IssuerController(IssuingEntityService issuingEntityService,
                            PersonService personService,
                            PersonDocumentService personDocumentService,
                            DocumentDefinitionService documentDefinitionService) {
        this.issuingEntityService = issuingEntityService;
        this.personService = personService;
        this.personDocumentService = personDocumentService;
        this.documentDefinitionService = documentDefinitionService;
    }

    @GetMapping("")
    public String home(@RequestParam(value = "issuerId", required = false) Long issuerId,
                       @RequestParam(value = "personId", required = false) Long personId,
                       Model model,
                       @ModelAttribute("msgOk") String msgOk,
                       @ModelAttribute("msgErr") String msgErr) {

        model.addAttribute("issuers", issuingEntityService.listApprovedEmitters()); // ajusta al nombre real
        model.addAttribute("idTypes", IdType.values());

        IssuerSearchForm searchForm = new IssuerSearchForm();
        searchForm.setIssuerId(issuerId);
        model.addAttribute("searchForm", searchForm);

        IssuerUploadForm uploadForm = new IssuerUploadForm();
        uploadForm.setIssuerId(issuerId);
        uploadForm.setPersonId(personId);
        model.addAttribute("uploadForm", uploadForm);

        if (issuerId != null && personId != null) {
            Person person = personService.findById(personId).orElse(null);
            model.addAttribute("person", person);
            model.addAttribute("personDocs", personDocumentService.listByPerson(personId));
            model.addAttribute("allowedDocs", documentDefinitionService.findAllowedByIssuer(issuerId));
        } else {
            model.addAttribute("person", null);
            model.addAttribute("personDocs", Collections.emptyList());
            model.addAttribute("allowedDocs", Collections.emptyList());
        }

        return "issuer/index";
    }

    @PostMapping("/search")
    public String search(@ModelAttribute("searchForm") IssuerSearchForm form,
                         RedirectAttributes ra) {

        if (form.getIssuerId() == null) {
            ra.addFlashAttribute("msgErr", "Selecciona un emisor.");
            return "redirect:/issuer";
        }

        var opt = personService.findByIdTypeAndNumber(form.getIdType(), form.getIdNumber());
        if (opt.isEmpty()) {
            ra.addFlashAttribute("msgErr", "Persona no encontrada.");
            return "redirect:/issuer?issuerId=" + form.getIssuerId();
        }

        return "redirect:/issuer?issuerId=" + form.getIssuerId() + "&personId=" + opt.get().getId();
    }

    @PostMapping("/upload")
    public String upload(@ModelAttribute("uploadForm") IssuerUploadForm form,
                         @RequestParam("file") MultipartFile file,
                         RedirectAttributes ra) {

        try {
            personDocumentService.uploadFromIssuer(
                    form.getIssuerId(),
                    form.getPersonId(),
                    form.getDocumentId(),
                    form.getStatus(),
                    form.getIssueDate(),
                    form.getExpiryDate(),
                    file
            );

            ra.addFlashAttribute("msgOk", "Documento cargado y enviado a revisión (PENDING).");
        } catch (Exception ex) {
            ra.addFlashAttribute("msgErr", "Error subiendo documento: " + ex.getMessage());
        }

        return "redirect:/issuer?issuerId=" + form.getIssuerId() + "&personId=" + form.getPersonId();
    }
}
