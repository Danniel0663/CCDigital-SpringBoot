package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.entity.ReviewStatus;
import co.edu.unbosque.ccdigital.service.PersonDocumentService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador web para acciones administrativas sobre documentos asociados a personas.
 *
 * <p>Actualmente expone operaciones bajo el prefijo {@code /admin/person-documents} para
 * realizar la revisiones de un documento cargado para una persona.</p>
 *
 * <p>La revisión actualiza el estado del documento (por ejemplo: aprobado/rechazado/pendiente)
 * y registra notas opcionales. La lógica de negocio se delega a {@link PersonDocumentService}.</p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
@Controller
@RequestMapping("/admin/person-documents")
public class PersonDocumentAdminController {

    /**
     * Servicio encargado de operaciones sobre documentos de personas, incluyendo revisión.
     */
    private final PersonDocumentService service;

    /**
     * Construye el controlador inyectando el servicio requerido.
     *
     * @param service servicio de documentos de persona
     */
    public PersonDocumentAdminController(PersonDocumentService service) {
        this.service = service;
    }

    /**
     * Realiza la revisión administrativa de un documento asociado a una persona.
     *
     * <p>Recibe el nuevo {@link ReviewStatus} y notas opcionales, delega al servicio la actualización,
     * y luego redirige al detalle de la persona para refrescar la UI.</p>
     *
     * <p>Retorna redirección a: {@code /admin/persons/{personId}}.</p>
     *
     * @param id identificador interno del registro de documento de persona a revisar
     * @param status nuevo estado de revisión a asignar (por ejemplo: APPROVED/REJECTED/PENDING)
     * @param notes notas opcionales asociadas a la revisión (puede ser {@code null})
     * @param personId identificador de la persona (usado únicamente para redirección a su detalle)
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
