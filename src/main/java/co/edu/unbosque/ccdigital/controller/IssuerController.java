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

/**
 * Controlador web del módulo de Emisores (Issuer).
 *
 * <p>Este módulo representa la experiencia de una entidad emisora (empresa/entidad)
 * que puede:</p>
 *
 * <p>Rutas expuestas bajo el prefijo {@code /issuer}.</p>
 *
 * <p><b>Mensajes UI:</b> utiliza {@link RedirectAttributes} con flash attributes
 * {@code msgOk} y {@code msgErr} para mostrar retroalimentación al usuario.</p>
 *
 * @author Danniel
 * @author Yeison 
 * @since 1.0
 */
@Controller
@RequestMapping("/issuer")
public class IssuerController {

    /**
     * Servicio para consultar emisores y sus reglas/catálogos.
     */
    private final IssuingEntityService issuingEntityService;

    /**
     * Servicio de personas.
     */
    private final PersonService personService;

    /**
     * Servicio de documentos de persona.
     */
    private final PersonDocumentService personDocumentService;

    /**
     * Servicio para consultar definiciones de documentos y los permitidos por emisor.
     */
    private final DocumentDefinitionService documentDefinitionService;

    /**
     * Construye el controlador del módulo Issuer inyectando dependencias.
     *
     * @param issuingEntityService servicio de emisores
     * @param personService servicio de personas
     * @param personDocumentService servicio de documentos asociados a personas
     * @param documentDefinitionService servicio de definiciones/catálogos de documentos
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
     * Página principal del módulo de emisores.
     *
     * <p>Carga al {@link Model}:</p>
     *
     * <p>Si {@code issuerId} y {@code personId} están presentes, se cargan los detalles de la persona
     * En caso contrario.</p>
     *
     * @param issuerId id del emisor seleccionado (opcional, llega por query param)
     * @param personId id de la persona encontrada/seleccionada (opcional, llega por query param)
     * @param model modelo de Spring MVC
     * @param msgOk mensaje de éxito proveniente de flash attributes (opcional)
     * @param msgErr mensaje de error proveniente de flash attributes (opcional)
     * @return nombre de la vista principal del módulo issuer
     */
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

    /**
     * Busca una persona a partir del emisor seleccionado y los datos de identificación ingresados.
     *
     * @param form formulario de búsqueda con emisor seleccionado y datos de identificación
     * @return redirección a la página principal con los parámetros necesarios
     */
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

    /**
     * Carga un documento desde un emisor para una persona específica.
     *
     * <p>Este endpoint recibe el formulario {@link IssuerUploadForm} con los metadatos del documento
     * y el archivo como {@link MultipartFile}. La carga y persistencia se delegan a
     * {@link PersonDocumentService#uploadFromIssuer}</p>
     *
     * @param form formulario de carga con emisor, persona, documento, estado y fechas
     * @param file archivo a cargar desde la UI
     * @param ra redirect attributes para mensajes flash
     */
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
