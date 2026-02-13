package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.entity.IssuingEntity;
import co.edu.unbosque.ccdigital.service.DocumentDefinitionService;
import co.edu.unbosque.ccdigital.service.IssuingEntityService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador web para la administración de entidades emisoras (Issuers) en el módulo administrativo.
 *
 * <p>
 * Expone endpoints bajo {@code /admin/issuers} para consultar estadísticas, ver detalle del emisor y
 * administrar la asignación de definiciones de documentos permitidos por emisor.
 * </p>
 *
 * @since 3.0
 */
@Controller
@RequestMapping("/admin/issuers")
public class IssuerAdminController {

    private final IssuingEntityService issuerService;
    private final DocumentDefinitionService documentService;

    /**
     * Constructor del controlador.
     *
     * @param issuerService servicio para operaciones sobre emisores
     * @param documentService servicio para consultar definiciones de documentos
     */
    public IssuerAdminController(IssuingEntityService issuerService,
                                 DocumentDefinitionService documentService) {
        this.issuerService = issuerService;
        this.documentService = documentService;
    }

    /**
     * Muestra la vista principal de emisores.
     *
     * @param model modelo de Spring MVC
     * @return nombre de la vista de emisores
     */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("stats", issuerService.stats());
        return "admin/issuers";
    }

    /**
     * Muestra el detalle de un emisor y el catálogo completo de documentos para permitir asignación/remoción.
     *
     * @param id identificador interno del emisor
     * @param model modelo de Spring MVC
     * @return nombre de la vista de detalle del emisor
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable("id") Long id, Model model) {
        IssuingEntity issuer = issuerService.getById(id);
        model.addAttribute("issuer", issuer);
        model.addAttribute("allDocs", documentService.findAll());
        return "admin/issuer-detail";
    }

    /**
     * Asocia una definición de documento a un emisor.
     *
     * @param id identificador interno del emisor
     * @param documentId identificador interno de la definición de documento
     * @return redirección al detalle del emisor
     */
    @PostMapping("/{id}/documents/add")
    public String addDoc(@PathVariable("id") Long id,
                         @RequestParam("documentId") Long documentId) {
        IssuingEntity issuer = issuerService.getById(id);
        issuerService.ensureIssuerHasDocument(issuer, documentId);
        return "redirect:/admin/issuers/" + id;
    }

    /**
     * Remueve la asociación entre un emisor y una definición de documento.
     *
     * @param id identificador interno del emisor
     * @param documentId identificador interno de la definición de documento
     * @return redirección al detalle del emisor
     */
    @PostMapping("/{id}/documents/remove")
    public String removeDoc(@PathVariable("id") Long id,
                            @RequestParam("documentId") Long documentId) {
        IssuingEntity issuer = issuerService.getById(id);
        issuerService.removeIssuerDocument(issuer, documentId);
        return "redirect:/admin/issuers/" + id;
    }
}
