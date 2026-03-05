package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.entity.AccessRequest;
import co.edu.unbosque.ccdigital.entity.AccessRequestStatus;
import co.edu.unbosque.ccdigital.entity.IdType;
import co.edu.unbosque.ccdigital.entity.Person;
import co.edu.unbosque.ccdigital.security.IndyUserPrincipal;
import co.edu.unbosque.ccdigital.service.AccessRequestService;
import co.edu.unbosque.ccdigital.repository.PersonRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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

        // Buscar la persona asociada a la identificación (mapeo usuario -> person)
        Person person = resolveAuthenticatedPerson(auth);

        // Si no hay person asociada, no se puede mostrar solicitudes
        if (person == null) {
            model.addAttribute("error", "No se encontró una persona asociada a su identificación");
            model.addAttribute("requests", java.util.Collections.emptyList());
            return "user/requests";
        }

        // Cargar solicitudes de la persona
        // Además de renderizar, exponemos una "señal" compacta para polling en frontend.
        // Si esta señal cambia, la UI se recarga automáticamente sin intervención del usuario.
        List<AccessRequest> requests = accessRequestService.listForPerson(person.getId());
        model.addAttribute("requests", requests);
        model.addAttribute("ccRequestsSignal", buildRequestsSignal(requests));
        return "user/requests";
    }

    /**
     * Señal ligera para detectar cambios en solicitudes del usuario sin recargar manualmente.
     *
     * <p>La UI consulta periódicamente este endpoint; si cambia la señal, recarga la vista para
     * reflejar nuevas solicitudes o decisiones.</p>
     *
     * @param auth autenticación actual
     * @return payload JSON con señal actual
     */
    @GetMapping("/user/requests/signal")
    @ResponseBody
    public Map<String, Object> requestsSignal(Authentication auth) {
        Person person = resolveAuthenticatedPerson(auth);
        if (person == null) {
            return Map.of("ok", false, "signal", "NO_PERSON");
        }
        // Endpoint ligero: no envía la lista completa, solo una firma de estado para detectar cambios.
        List<AccessRequest> requests = accessRequestService.listForPerson(person.getId());
        return Map.of("ok", true, "signal", buildRequestsSignal(requests));
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

    private Person resolveAuthenticatedPerson(Authentication auth) {
        IndyUserPrincipal principal = (IndyUserPrincipal) auth.getPrincipal();
        return personRepository
                .findByIdTypeAndIdNumber(IdType.valueOf(principal.getIdType()), principal.getIdNumber())
                .orElse(null);
    }

    private String buildRequestsSignal(List<AccessRequest> requests) {
        // Construye una firma estable con métricas y timestamps clave.
        // Cualquier cambio funcional (nueva solicitud o cambio de estado) modifica esta cadena.
        long total = requests == null ? 0 : requests.size();
        long pending = countByStatus(requests, AccessRequestStatus.PENDIENTE);
        long approved = countByStatus(requests, AccessRequestStatus.APROBADA);
        long rejected = countByStatus(requests, AccessRequestStatus.RECHAZADA);
        long expired = countByStatus(requests, AccessRequestStatus.EXPIRADA);

        long maxId = requests == null ? 0 :
                requests.stream()
                        .map(AccessRequest::getId)
                        .filter(java.util.Objects::nonNull)
                        .mapToLong(Long::longValue)
                        .max()
                        .orElse(0);

        LocalDateTime maxRequestedAt = maxDateTime(requests, true);
        LocalDateTime maxDecidedAt = maxDateTime(requests, false);

        return total + "|" + pending + "|" + approved + "|" + rejected + "|" + expired + "|" + maxId
                + "|" + (maxRequestedAt != null ? maxRequestedAt : "-")
                + "|" + (maxDecidedAt != null ? maxDecidedAt : "-");
    }

    private long countByStatus(List<AccessRequest> requests, AccessRequestStatus status) {
        if (requests == null || requests.isEmpty()) {
            return 0;
        }
        return requests.stream().filter(r -> r != null && r.getStatus() == status).count();
    }

    private LocalDateTime maxDateTime(List<AccessRequest> requests, boolean requestedAt) {
        if (requests == null || requests.isEmpty()) {
            return null;
        }
        return requests.stream()
                .map(r -> requestedAt ? r.getRequestedAt() : r.getDecidedAt())
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }
}
