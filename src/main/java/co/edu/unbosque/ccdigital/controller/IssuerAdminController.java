package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.entity.IssuingEntity;
import co.edu.unbosque.ccdigital.service.DocumentDefinitionService;
import co.edu.unbosque.ccdigital.service.IssuingEntityService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/issuers")
public class IssuerAdminController {

    private final IssuingEntityService issuerService;
    private final DocumentDefinitionService documentService;

    public IssuerAdminController(IssuingEntityService issuerService,
                                 DocumentDefinitionService documentService) {
        this.issuerService = issuerService;
        this.documentService = documentService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("stats", issuerService.stats());
        return "admin/issuers";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        IssuingEntity issuer = issuerService.getById(id);
        model.addAttribute("issuer", issuer);
        model.addAttribute("allDocs", documentService.findAll());
        return "admin/issuer-detail";
    }

    @PostMapping("/{id}/documents/add")
    public String addDoc(@PathVariable Long id, @RequestParam Long documentId) {
        IssuingEntity issuer = issuerService.getById(id);
        issuerService.ensureIssuerHasDocument(issuer, documentId);
        return "redirect:/admin/issuers/" + id;
    }

    @PostMapping("/{id}/documents/remove")
    public String removeDoc(@PathVariable Long id, @RequestParam Long documentId) {
        IssuingEntity issuer = issuerService.getById(id);
        issuerService.removeIssuerDocument(issuer, documentId);
        return "redirect:/admin/issuers/" + id;
    }
}
