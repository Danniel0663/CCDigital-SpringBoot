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

/**
 * Controlador MVC para el portal del emisor.
 *
 * <p>Permite buscar personas por identificación y cargar documentos autorizados
 * para revisión posterior.</p>
 *
 * @since 1.0.0
 */
@Controller
@RequestMapping("/issuer")
public class IssuerController {

    private final IssuingEntityService issuingEntityService;
    private final PersonService personService;
    private final PersonDocumentService personDocumentService;
    private final DocumentDefinitionService documentDefinitionService;

    /**
     * Constructor con inyección de dependencias.
     *
     * @param issuingEntityService servicio de emisores
     * @param personService servicio de personas
     * @param personDocumentService servicio de documentos por persona
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
     * Obtiene el identificador del emisor autenticado a partir del principal de seguridad.
     *
     * @return identificador del emisor autenticado
     * @throws IllegalStateException si no existe un principal de emisor autenticado
     */
    private Long currentIssuerId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth.getPrincipal();
        if (principal instanceof IssuerPrincipal ip) {
            return ip.getIssuerId();
        }
        throw new IllegalStateException("No hay un emisor autenticado.");
    }

    /**
     * Vista principal del portal del emisor. Si se proporciona {@code personId},
     * carga información de la persona y los documentos permitidos para el emisor.
     *
     * @param personId identificador de la persona (opcional)
     * @param model modelo MVC
     * @param msgOk mensaje de confirmación (flash)
     * @param msgErr mensaje de error (flash)
     * @return vista principal del emisor
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

        IssuerSearchForm searchForm = new IssuerSearchForm();
        model.addAttribute("searchForm", searchForm);

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

        return "issuer/index";
    }

    /**
     * Busca una persona por tipo y número de identificación y redirige a la vista principal con su {@code personId}.
     *
     * @param form formulario de búsqueda
     * @param ra atributos flash para mensajes de resultado
     * @return redirección a la vista principal del emisor
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
     * Carga un documento emitido por el emisor para una persona específica y lo registra con estado de revisión.
     *
     * @param form formulario con metadatos de carga
     * @param file archivo a cargar
     * @param ra atributos flash para mensajes de resultado
     * @return redirección a la vista principal del emisor para la persona
     */
    @PostMapping("/upload")
    public String upload(@ModelAttribute("uploadForm") IssuerUploadForm form,
                         @RequestParam("file") MultipartFile file,
                         RedirectAttributes ra) {

        Long issuerId = currentIssuerId();

        try {
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
        } catch (Exception ex) {
            ra.addFlashAttribute("msgErr", "Error al cargar el documento: " + ex.getMessage());
        }

        return "redirect:/issuer?personId=" + form.getPersonId();
    }
}
