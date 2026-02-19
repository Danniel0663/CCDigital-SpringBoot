package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.entity.IdType;
import co.edu.unbosque.ccdigital.entity.Person;
import co.edu.unbosque.ccdigital.security.IndyUserPrincipal;
import co.edu.unbosque.ccdigital.service.AccessRequestService;
import co.edu.unbosque.ccdigital.repository.PersonRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador del módulo Usuario para revisar solicitudes de acceso a documentos.
 *
 * Responsabilidad:
 * - Lista solicitudes que entidades emisoras han creado para consultar documentos del usuario.
 * - Permite aprobar o rechazar solicitudes pendientes.
 *
 * Importante:
 * - La identidad del usuario se obtiene desde IndyUserPrincipal (idType e idNumber).
 * - Con esa identificación se busca la entidad Person en base de datos.
 * - El servicio valida que la solicitud corresponda a esa persona y que esté en estado correcto.
 */
@Controller
public class UserAccessRequestController {

    /**
     * Repositorio de personas, usado para ubicar la persona asociada al usuario autenticado.
     */
    private final PersonRepository personRepository;

    /**
     * Servicio de solicitudes de acceso que contiene la lógica de negocio para:
     * - Listar solicitudes por persona.
     * - Aprobar/rechazar (decidir) una solicitud.
     */
    private final AccessRequestService accessRequestService;

    /**
     * Constructor con inyección de dependencias.
     *
     * @param personRepository Repositorio de personas
     * @param accessRequestService Servicio de solicitudes de acceso
     */
    public UserAccessRequestController(PersonRepository personRepository, AccessRequestService accessRequestService) {
        this.personRepository = personRepository;
        this.accessRequestService = accessRequestService;
    }

    /**
     * Lista las solicitudes de acceso asociadas al usuario autenticado.
     *
     * Flujo:
     * - Lee principal (IndyUserPrincipal).
     * - Busca la Person correspondiente en BD.
     * - Si existe, consulta solicitudes para esa Person.
     * - Si no existe, muestra mensaje de error.
     *
     * @param auth  Autenticación de Spring Security
     * @param error Mensaje de error opcional pasado por query param (redirect con error)
     * @param model Modelo de vista
     * @return Vista Thymeleaf "user/requests"
     */
    @GetMapping("/user/requests")
    public String requests(Authentication auth, @RequestParam(required = false) String error, Model model) {

        // Error opcional (viene desde redirect cuando falla approve/reject)
        if (error != null && !error.isBlank()) {
            model.addAttribute("error", error);
        }

        // Principal del usuario autenticado (contiene idType e idNumber)
        IndyUserPrincipal principal = (IndyUserPrincipal) auth.getPrincipal();

        // Buscar la persona asociada a la identificación (mapeo usuario -> person)
        Person person = personRepository
                .findByIdTypeAndIdNumber(IdType.valueOf(principal.getIdType()), principal.getIdNumber())
                .orElse(null);

        // Si no hay person asociada, no se puede mostrar solicitudes
        if (person == null) {
            model.addAttribute("error", "No se encontró una persona asociada a su identificación");
            model.addAttribute("requests", java.util.Collections.emptyList());
            return "user/requests";
        }

        // Cargar solicitudes de la persona
        model.addAttribute("requests", accessRequestService.listForPerson(person.getId()));
        return "user/requests";
    }

    /**
     * Aprueba una solicitud de acceso (solo si está en estado PENDIENTE y pertenece al usuario).
     *
     * @param auth         Autenticación de Spring Security (usuario)
     * @param requestId    ID de la solicitud a aprobar
     * @param decisionNote Nota opcional del usuario (comentario/observación)
     * @return Redirección al listado; si hay error, redirige con error en query param
     */
    @PostMapping("/user/requests/{requestId}/approve")
    public String approve(
            Authentication auth,
            @PathVariable Long requestId,
            @RequestParam(required = false) String decisionNote
    ) {

        IndyUserPrincipal principal = (IndyUserPrincipal) auth.getPrincipal();

        // Se exige que la persona exista (a diferencia del GET que retorna vista con error)
        Person person = personRepository
                .findByIdTypeAndIdNumber(IdType.valueOf(principal.getIdType()), principal.getIdNumber())
                .orElseThrow(() -> new IllegalArgumentException("Persona no encontrada"));

        try {
            // true = aprobar
            accessRequestService.decide(requestId, person.getId(), true, decisionNote);
            return "redirect:/user/requests";
        } catch (IllegalArgumentException ex) {
            // Se usa URL encoding para evitar problemas con caracteres especiales en la redirección
            return "redirect:/user/requests?error=" +
                    java.net.URLEncoder.encode(ex.getMessage(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /**
     * Rechaza una solicitud de acceso (solo si está en estado PENDIENTE y pertenece al usuario).
     *
     * @param auth         Autenticación de Spring Security (usuario)
     * @param requestId    ID de la solicitud a rechazar
     * @param decisionNote Nota opcional (motivo de rechazo u observación)
     * @return Redirección al listado; si hay error, redirige con error en query param
     */
    @PostMapping("/user/requests/{requestId}/reject")
    public String reject(
            Authentication auth,
            @PathVariable Long requestId,
            @RequestParam(required = false) String decisionNote
    ) {

        IndyUserPrincipal principal = (IndyUserPrincipal) auth.getPrincipal();

        // Se exige que la persona exista
        Person person = personRepository
                .findByIdTypeAndIdNumber(IdType.valueOf(principal.getIdType()), principal.getIdNumber())
                .orElseThrow(() -> new IllegalArgumentException("Persona no encontrada"));

        try {
            // false = rechazar
            accessRequestService.decide(requestId, person.getId(), false, decisionNote);
            return "redirect:/user/requests";
        } catch (IllegalArgumentException ex) {
            return "redirect:/user/requests?error=" +
                    java.net.URLEncoder.encode(ex.getMessage(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
