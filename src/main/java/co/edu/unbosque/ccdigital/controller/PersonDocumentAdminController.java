package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.entity.ReviewStatus;
import co.edu.unbosque.ccdigital.service.PersonDocumentService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador MVC administrativo para el flujo de revisión de documentos por persona.
 *
 * @since 1.0.0
 */
@Controller
@RequestMapping("/admin/person-documents")
public class PersonDocumentAdminController {

    private final PersonDocumentService service;

    /**
     * Constructor con inyección del servicio.
     *
     * @param service servicio de documentos por persona
     */
    public PersonDocumentAdminController(PersonDocumentService service) {
        this.service = service;
    }

    /**
     * Registra el resultado de revisión de un documento de persona y redirige al detalle de la persona.
     *
     * @param id identificador del registro PersonDocument
     * @param status estado de revisión
     * @param notes observaciones de revisión (opcional)
     * @param personId identificador de la persona para redirección
     * @return redirección al detalle de la persona
     */
    @PostMapping("/{id}/review")
    public String review(@PathVariable Long id,
                         @RequestParam("status") ReviewStatus status,
                         @RequestParam(value = "notes", required = false) String notes,
                         @RequestParam("personId") Long personId) {

        service.review(id, status, notes);
        return "redirect:/admin/persons/" + personId;
    }
}
