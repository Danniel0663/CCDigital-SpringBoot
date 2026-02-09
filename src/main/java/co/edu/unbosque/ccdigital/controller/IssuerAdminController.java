package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.entity.IssuingEntity;
import co.edu.unbosque.ccdigital.repository.EntityUserRepository;
import co.edu.unbosque.ccdigital.service.DocumentDefinitionService;
import co.edu.unbosque.ccdigital.service.IssuerAccountService;
import co.edu.unbosque.ccdigital.service.IssuingEntityService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador MVC administrativo para gestionar emisores y sus permisos.
 *
 * @since 1.0.0
 */
@Controller
@RequestMapping("/admin/issuers")
public class IssuerAdminController {

    private final IssuingEntityService issuerService;
    private final DocumentDefinitionService documentService;
    private final IssuerAccountService issuerAccountService;
    private final EntityUserRepository entityUserRepository;

    /**
     * Constructor con inyección de dependencias.
     *
     * @param issuerService servicio de emisores
     * @param documentService servicio de definiciones de documentos
     * @param issuerAccountService servicio de credenciales de emisores
     * @param entityUserRepository repositorio de usuarios emisores
     */
    public IssuerAdminController(IssuingEntityService issuerService,
                                 DocumentDefinitionService documentService,
                                 IssuerAccountService issuerAccountService,
                                 EntityUserRepository entityUserRepository) {
        this.issuerService = issuerService;
        this.documentService = documentService;
        this.issuerAccountService = issuerAccountService;
        this.entityUserRepository = entityUserRepository;
    }

    /**
     * Muestra el listado y estadísticas generales de emisores.
     *
     * @param model modelo MVC
     * @return vista de emisores
     */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("stats", issuerService.stats());
        return "admin/issuers";
    }

    /**
     * Muestra el detalle de un emisor, su usuario asociado y documentos disponibles.
     *
     * @param id identificador del emisor
     * @param model modelo MVC
     * @param credOk indicador de operación exitosa
     * @param credErr mensaje de error si aplica
     * @return vista de detalle de emisor
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model,
                         @RequestParam(value = "credOk", required = false) String credOk,
                         @RequestParam(value = "credErr", required = false) String credErr) {
        IssuingEntity issuer = issuerService.getById(id);
        model.addAttribute("issuer", issuer);
        model.addAttribute("entityUser", entityUserRepository.findById(id).orElse(null));
        model.addAttribute("allDocs", documentService.findAll());
        model.addAttribute("credOk", credOk);
        model.addAttribute("credErr", credErr);
        return "admin/issuer-detail";
    }

    /**
     * Asocia un tipo de documento permitido a un emisor.
     *
     * @param id identificador del emisor
     * @param documentId identificador del documento
     * @return redirección al detalle del emisor
     */
    @PostMapping("/{id}/documents/add")
    public String addDoc(@PathVariable Long id, @RequestParam Long documentId) {
        IssuingEntity issuer = issuerService.getById(id);
        issuerService.ensureIssuerHasDocument(issuer, documentId);
        return "redirect:/admin/issuers/" + id;
    }

    /**
     * Elimina la asociación de un tipo de documento permitido para un emisor.
     *
     * @param id identificador del emisor
     * @param documentId identificador del documento
     * @return redirección al detalle del emisor
     */
    @PostMapping("/{id}/documents/remove")
    public String removeDoc(@PathVariable Long id, @RequestParam Long documentId) {
        IssuingEntity issuer = issuerService.getById(id);
        issuerService.removeIssuerDocument(issuer, documentId);
        return "redirect:/admin/issuers/" + id;
    }

    /**
     * Establece credenciales de acceso para el usuario emisor.
     *
     * @param id identificador del emisor
     * @param email correo del usuario emisor
     * @param password contraseña a registrar
     * @return redirección al detalle del emisor indicando resultado
     */
    @PostMapping("/{id}/credentials")
    public String saveCredentials(@PathVariable Long id,
                                  @RequestParam("email") String email,
                                  @RequestParam("password") String password) {
        try {
            issuerAccountService.setCredentials(id, email, password);
            return "redirect:/admin/issuers/" + id + "?credOk=1";
        } catch (Exception ex) {
            return "redirect:/admin/issuers/" + id + "?credErr=" + ex.getMessage();
        }
    }
}
