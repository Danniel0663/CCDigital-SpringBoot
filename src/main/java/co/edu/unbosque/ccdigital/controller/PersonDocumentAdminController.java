package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.entity.ReviewStatus;
import co.edu.unbosque.ccdigital.service.PersonDocumentService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador web para acciones administrativas sobre documentos asociados a personas.
 *
 * <p>
 * Expone operaciones bajo {@code /admin/person-documents} para revisar documentos cargados,
 * actualizando su estado y registrando observaciones.
 * </p>
 *
 * @since 3.0
 */
@Controller
@RequestMapping("/admin/person-documents")
public class PersonDocumentAdminController {

    private final PersonDocumentService service;

    /**
     * Constructor del controlador.
     *
     * @param service servicio de documentos de persona
     */
    public PersonDocumentAdminController(PersonDocumentService service) {
        this.service = service;
    }

    /**
     * Registra la revisión de un documento asociado a una persona y redirige al detalle de la persona.
     *
     * @param id identificador del registro {@code PersonDocument}
     * @param status estado de revisión a asignar
     * @param notes observaciones de revisión (opcional)
     * @param personId id de la persona, usado para redirección
     * @return redirección al detalle de la persona
     */
    @PostMapping("/{id}/review")
    public String review(@PathVariable("id") Long id,
                         @RequestParam("status") ReviewStatus status,
                         @RequestParam(value = "notes", required = false) String notes,
                         @RequestParam("personId") Long personId) {

        service.review(id, status, notes);
        return "redirect:/admin/persons/" + personId;
    }
}
