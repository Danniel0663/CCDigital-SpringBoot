package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.entity.IssuingEntity;
import co.edu.unbosque.ccdigital.service.DocumentDefinitionService;
import co.edu.unbosque.ccdigital.service.IssuingEntityService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador web para la administración de entidades emisoras (Issuers)
 * dentro del módulo administrativo.
 *
 * <p>Expone endpoints bajo el prefijo {@code /admin/issuers} para:</p>
 *
 * <p><b>Responsabilidad:</b> este controlador enruta peticiones y arma el {@link Model} para las vistas.
 * La lógica de negocio (consultas, validaciones y persistencia) se delega a
 * {@link IssuingEntityService} y {@link DocumentDefinitionService}.</p>
 *
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
@Controller
@RequestMapping("/admin/issuers")
public class IssuerAdminController {

    /**
     * Servicio de negocio encargado de operaciones sobre {@link IssuingEntity}.
     */
    private final IssuingEntityService issuerService;

    /**
     * Servicio para consultar el catálogo completo de definiciones de documentos disponibles.
     */
    private final DocumentDefinitionService documentService;

    /**
     * Construye el controlador de administración de emisores inyectando sus dependencias.
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
     * <p>Agrega al {@link Model}:</p>
     *
     * @param model modelo de Spring MVC usado para enviar atributos a la vista
     * @return nombre de la vista de listado/estadísticas de emisores
     */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("stats", issuerService.stats());
        return "admin/issuers";
    }

    /**
     * Muestra el detalle de un emisor y el catálogo completo de documentos para permitir
     * asignación/remoción desde la UI.
     *
     * <p>Agrega al {@link Model}:</p>
     * <ul>
     *   <li>{@code issuer}: emisor consultado por id.</li>
     *   <li>{@code allDocs}: listado de todas las definiciones de documentos disponibles.</li>
     * </ul>
     *
     * @param id identificador interno del emisor
     * @param model modelo de Spring MVC
     * @return nombre de la vista de detalle del emisor
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        IssuingEntity issuer = issuerService.getById(id);
        model.addAttribute("issuer", issuer);
        model.addAttribute("allDocs", documentService.findAll());
        return "admin/issuer-detail";
    }

    /**
     * Asocia una definición de documento a un emisor.
     *
     * <p>Esta operación delega en {@link IssuingEntityService#ensureIssuerHasDocument(IssuingEntity, Long)}
     * para garantizar idempotencia.</p>
     *
     * @param id identificador interno del emisor
     * @param documentId identificador interno de la definición de documento a asociar
     * @return redirección al detalle del emisor: {@code /admin/issuers/{id}}
     */
    @PostMapping("/{id}/documents/add")
    public String addDoc(@PathVariable Long id, @RequestParam Long documentId) {
        IssuingEntity issuer = issuerService.getById(id);
        issuerService.ensureIssuerHasDocument(issuer, documentId);
        return "redirect:/admin/issuers/" + id;
    }

    /**
     * Remueve la asociación entre un emisor y una definición de documento.
     *
     * <p>La operación delega en {@link IssuingEntityService#removeIssuerDocument(IssuingEntity, Long)}
     * para eliminar la relación.</p>
     *
     * @param id identificador interno del emisor
     * @param documentId identificador interno de la definición de documento a remover
     * @return redirección al detalle del emisor: {@code /admin/issuers/{id}}
     */
    @PostMapping("/{id}/documents/remove")
    public String removeDoc(@PathVariable Long id, @RequestParam Long documentId) {
        IssuingEntity issuer = issuerService.getById(id);
        issuerService.removeIssuerDocument(issuer, documentId);
        return "redirect:/admin/issuers/" + id;
    }
}
