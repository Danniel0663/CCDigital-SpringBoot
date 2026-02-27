package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.dto.IssuerSearchForm;
import co.edu.unbosque.ccdigital.dto.IssuerUploadForm;
import co.edu.unbosque.ccdigital.entity.IdType;
import co.edu.unbosque.ccdigital.entity.IssuingEntity;
import co.edu.unbosque.ccdigital.entity.Person;
import co.edu.unbosque.ccdigital.security.IssuerPrincipal;
import co.edu.unbosque.ccdigital.service.DocumentDefinitionService;
import co.edu.unbosque.ccdigital.service.IssuingEntityService;
import co.edu.unbosque.ccdigital.service.PersonDocumentService;
import co.edu.unbosque.ccdigital.service.PersonService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.Locale;

/**
 * Controlador web para el módulo de Emisor (Issuer).
 *
 * <p>
 * Permite al emisor autenticado buscar una persona y cargar documentos dentro del conjunto
 * de definiciones permitidas para su entidad emisora.
 * </p>
 *
 * @since 3.0
 */
@Controller
@RequestMapping("/issuer")
public class IssuerController {

    private final IssuingEntityService issuingEntityService;
    private final PersonService personService;
    private final PersonDocumentService personDocumentService;
    private final DocumentDefinitionService documentDefinitionService;

    /**
     * Constructor del controlador.
     *
     * @param issuingEntityService servicio de emisores
     * @param personService servicio de personas
     * @param personDocumentService servicio de documentos de persona
     * @param documentDefinitionService servicio de definiciones de documentos
     */
    public IssuerController(IssuingEntityService issuingEntityService,
                            PersonService personService,
                            PersonDocumentService personDocumentService,
                            DocumentDefinitionService documentDefinitionService) {
        this.issuingEntityService = issuingEntityService;
        this.personService = personService;
        this.personDocumentService = personDocumentService;
        this.documentDefinitionService = documentDefinitionService;
    }

    /**
     * Obtiene el identificador del emisor autenticado a partir del {@link IssuerPrincipal}.
     *
     * @return id del emisor autenticado
     * @throws IllegalStateException si no existe un emisor autenticado en el contexto
     */
    private Long currentIssuerId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth != null ? auth.getPrincipal() : null;

        if (principal instanceof IssuerPrincipal ip) {
            return ip.getIssuerId();
        }
        throw new IllegalStateException("No hay un emisor autenticado.");
    }

    /**
     * Página principal del emisor.
     *
     * <p>
     * Si se envía {@code personId}, carga la persona y sus documentos para habilitar el flujo de carga.
     * </p>
     *
     * @param personId id interno de la persona (opcional)
     * @param model modelo de Spring MVC
     * @param msgOk mensaje de éxito (flash attribute)
     * @param msgErr mensaje de error (flash attribute)
     * @return vista principal del módulo issuer
     */
    @GetMapping("")
    public String home(@RequestParam(value = "personId", required = false) Long personId,
                       Model model,
                       @ModelAttribute("msgOk") String msgOk,
                       @ModelAttribute("msgErr") String msgErr) {

        Long issuerId = currentIssuerId();
        IssuingEntity issuer = issuingEntityService.getById(issuerId);

        model.addAttribute("issuer", issuer);
        model.addAttribute("idTypes", IdType.values());
        model.addAttribute("searchForm", new IssuerSearchForm());

        IssuerUploadForm uploadForm = new IssuerUploadForm();
        uploadForm.setPersonId(personId);
        model.addAttribute("uploadForm", uploadForm);

        if (personId != null) {
            Person person = personService.findById(personId).orElse(null);
            model.addAttribute("person", person);
            model.addAttribute("personDocs", personDocumentService.listByPerson(personId));
            model.addAttribute("allowedDocs", documentDefinitionService.findAllowedByIssuer(issuerId));
        } else {
            model.addAttribute("person", null);
            model.addAttribute("personDocs", Collections.emptyList());
            model.addAttribute("allowedDocs", Collections.emptyList());
        }

        model.addAttribute("msgOk", msgOk);
        model.addAttribute("msgErr", msgErr);

        return "issuer/index";
    }

    /**
     * Busca una persona por tipo y número de identificación.
     *
     * @param form formulario de búsqueda
     * @param ra atributos flash para mensajes de UI
     * @return redirección a la página principal con {@code personId} si la persona existe
     */
    @PostMapping("/search")
    public String search(@ModelAttribute("searchForm") IssuerSearchForm form,
                         RedirectAttributes ra) {

        var opt = personService.findByIdTypeAndNumber(form.getIdType(), form.getIdNumber());
        if (opt.isEmpty()) {
            ra.addFlashAttribute("msgErr", "Persona no encontrada.");
            return "redirect:/issuer";
        }

        return "redirect:/issuer?personId=" + opt.get().getId();
    }

    /**
     * Carga un documento para una persona desde el módulo del emisor.
     *
     * @param form formulario con metadatos del documento
     * @param file archivo recibido desde la UI
     * @param ra atributos flash para mensajes de UI
     * @return redirección a la página principal del emisor con el contexto de la persona
     */
    @PostMapping("/upload")
    public String upload(@ModelAttribute("uploadForm") IssuerUploadForm form,
                         @RequestParam(value = "file", required = false) MultipartFile file,
                         RedirectAttributes ra) {
        Long personId = form != null ? form.getPersonId() : null;
        try {
            Long issuerId = currentIssuerId();
            if (!isPdfUpload(file)) {
                ra.addFlashAttribute("msgErr", "Solo se permite subir archivos PDF.");
                return "redirect:/issuer" + (personId != null ? "?personId=" + personId : "");
            }
            personDocumentService.uploadFromIssuer(
                    issuerId,
                    form.getPersonId(),
                    form.getDocumentId(),
                    form.getStatus(),
                    form.getIssueDate(),
                    form.getExpiryDate(),
                    file
            );
            ra.addFlashAttribute("msgOk", "Documento cargado y enviado a revisión.");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("msgErr", ex.getMessage());
        } catch (Exception ex) {
            ra.addFlashAttribute("msgErr", "Error al cargar el documento: " + ex.getMessage());
        }

        return "redirect:/issuer" + (personId != null ? "?personId=" + personId : "");
    }

    /**
     * Validación ligera en controlador para aceptar únicamente archivos PDF desde el formulario emisor.
     *
     * <p>La validación definitiva se mantiene en servicio; aquí se busca devolver un mensaje amigable
     * antes de ejecutar el flujo de carga.</p>
     *
     * @param file archivo recibido
     * @return {@code true} si parece PDF por nombre o mime type
     */
    private boolean isPdfUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) return false;
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim().toLowerCase(Locale.ROOT);
        String mime = file.getContentType() == null ? "" : file.getContentType().trim().toLowerCase(Locale.ROOT);
        return name.endsWith(".pdf") || mime.contains("pdf");
    }
}
