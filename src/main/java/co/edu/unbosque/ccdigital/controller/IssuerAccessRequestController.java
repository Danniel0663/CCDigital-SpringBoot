package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.entity.IdType;
import co.edu.unbosque.ccdigital.entity.Person;
import co.edu.unbosque.ccdigital.entity.PersonDocument;
import co.edu.unbosque.ccdigital.security.IssuerPrincipal;
import co.edu.unbosque.ccdigital.service.AccessRequestService;
import co.edu.unbosque.ccdigital.repository.PersonDocumentRepository;
import co.edu.unbosque.ccdigital.repository.PersonRepository;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

/**
 * Controlador del módulo Emisor para gestionar solicitudes de acceso a documentos.
 *
 * Responsabilidad:
 * - Permite que un emisor autenticado cree una solicitud para consultar documentos de una persona.
 * - Lista las solicitudes creadas por el emisor.
 * - Permite visualizar un documento únicamente si la solicitud fue aprobada por el usuario.
 *
 * Flujo general:
 * 1) Emisor entra a "Nueva solicitud" y busca la persona por tipo y número de identificación.
 * 2) El sistema muestra los documentos aprobados disponibles de esa persona para seleccionar.
 * 3) El emisor crea la solicitud con un motivo y uno o más documentos seleccionados.
 * 4) El usuario (ciudadano) aprueba o rechaza desde el módulo Usuario.
 * 5) Si el usuario aprueba, el emisor puede abrir/visualizar los documentos solicitados.
 */
@Controller
public class IssuerAccessRequestController {

    /**
     * Repositorio de personas (tabla persons).
     * Se usa para localizar el registro de la persona por su identificación.
     */
    private final PersonRepository personRepository;

    /**
     * Repositorio de documentos asociados a una persona (person_documents).
     * Se usa para listar documentos aprobados disponibles para consulta.
     */
    private final PersonDocumentRepository personDocumentRepository;

    /**
     * Servicio de dominio para solicitudes de acceso.
     * Contiene la lógica central: crear solicitudes, listar por emisor/persona y
     * verificar permisos/estados para visualizar documentos.
     */
    private final AccessRequestService accessRequestService;

    /**
     * Constructor con inyección de dependencias.
     *
     * @param personRepository Repositorio de personas
     * @param personDocumentRepository Repositorio de documentos de persona
     * @param accessRequestService Servicio de solicitudes de acceso
     */
    public IssuerAccessRequestController(
            PersonRepository personRepository,
            PersonDocumentRepository personDocumentRepository,
            AccessRequestService accessRequestService
    ) {
        this.personRepository = personRepository;
        this.personDocumentRepository = personDocumentRepository;
        this.accessRequestService = accessRequestService;
    }

    /**
     * Muestra el listado de solicitudes creadas por el emisor autenticado.
     *
     * @param auth  Autenticación de Spring Security (contiene el principal del emisor)
     * @param model Modelo para la vista
     * @return Nombre de la vista Thymeleaf con el listado
     */
    @GetMapping("/issuer/access-requests")
    public String list(Authentication auth, Model model) {
        // Se obtiene el emisor autenticado desde el principal de seguridad
        IssuerPrincipal issuer = (IssuerPrincipal) auth.getPrincipal();

        // Se consulta en el servicio las solicitudes asociadas a la entidad del emisor
        model.addAttribute("requests", accessRequestService.listForEntity(issuer.getIssuerId()));

        // Retorna la vista que renderiza el listado de solicitudes
        return "issuer/access-requests";
    }

    /**
     * Pantalla para crear una nueva solicitud.
     *
     * Se comporta de dos formas:
     * - Si no viene idType/idNumber: renderiza el formulario vacío.
     * - Si vienen idType/idNumber: busca la persona y carga sus documentos aprobados disponibles.
     *
     * @param idType   Tipo de identificación (String) enviado como query param (ej: CC, TI)
     * @param idNumber Número de identificación enviado como query param
     * @param model    Modelo para la vista
     * @return Vista Thymeleaf de nueva solicitud
     */
    @GetMapping("/issuer/access-requests/new")
    public String newRequest(
            @RequestParam(required = false) String idType,
            @RequestParam(required = false) String idNumber,
            Model model
    ) {

        // Se conservan los valores ingresados (para re-pintarlos en el formulario)
        model.addAttribute("idType", idType);
        model.addAttribute("idNumber", idNumber);

        // Si no hay datos de búsqueda, se carga la pantalla sin persona ni documentos
        if (idType == null || idType.isBlank() || idNumber == null || idNumber.isBlank()) {
            model.addAttribute("person", null);
            model.addAttribute("personDocuments", Collections.emptyList());
            return "issuer/access-requests-new";
        }

        try {
            // Se valida y convierte el idType a enum. Si no existe, lanzará IllegalArgumentException
            IdType enumIdType = IdType.valueOf(idType);

            // Se busca la persona por tipo y número de documento
            Person person = personRepository.findByIdTypeAndIdNumber(enumIdType, idNumber).orElse(null);

            // Si no existe, se retorna la vista con un error entendible
            if (person == null) {
                model.addAttribute("error", "No se encontró la persona con la identificación indicada");
                model.addAttribute("person", null);
                model.addAttribute("personDocuments", Collections.emptyList());
                return "issuer/access-requests-new";
            }

            // Se listan únicamente documentos aprobados de la persona (y con archivos asociados)
            List<PersonDocument> docs = personDocumentRepository.findApprovedByPersonIdWithFiles(person.getId());

            model.addAttribute("person", person);
            model.addAttribute("personDocuments", docs);

            return "issuer/access-requests-new";
        } catch (IllegalArgumentException ex) {
            // Ocurre si el idType no coincide con el enum IdType
            model.addAttribute("error", "Tipo de identificación no válido");
            model.addAttribute("person", null);
            model.addAttribute("personDocuments", Collections.emptyList());
            return "issuer/access-requests-new";
        }
    }

    /**
     * Recibe el post del formulario "Buscar persona" y redirige a /new con query params.
     * Esto permite que la búsqueda quede reflejada en la URL y que el GET cargue persona/documentos.
     *
     * @param idType   Tipo de identificación
     * @param idNumber Número de identificación
     * @return Redirección al GET /issuer/access-requests/new con los parámetros
     */
    @PostMapping("/issuer/access-requests/search")
    public String search(@RequestParam String idType, @RequestParam String idNumber) {
        return "redirect:/issuer/access-requests/new?idType=" + idType + "&idNumber=" + idNumber;
    }

    /**
     * Crea la solicitud de acceso.
     *
     * Reglas básicas:
     * - Debe seleccionar al menos un documento.
     * - El servicio valida que los documentos seleccionados correspondan a la persona y que estén aprobados.
     *
     * Si hay error:
     * - Renderiza nuevamente la vista de creación con el mensaje de error y recarga la persona/documentos.
     *
     * @param auth              Autenticación del emisor
     * @param personId          ID interno de la persona (tabla persons)
     * @param purpose           Motivo de la solicitud
     * @param personDocumentIds IDs de person_documents seleccionados (checkboxes)
     * @param model             Modelo para la vista
     * @return Redirección al listado si fue exitoso; o vista de creación con error si no
     */
    @PostMapping("/issuer/access-requests")
    public String create(
            Authentication auth,
            @RequestParam Long personId,
            @RequestParam String purpose,
            @RequestParam(name = "personDocumentIds", required = false) List<Long> personDocumentIds,
            Model model
    ) {
        IssuerPrincipal issuer = (IssuerPrincipal) auth.getPrincipal();

        try {
            // Validación mínima a nivel controlador (evita crear solicitudes vacías)
            if (personDocumentIds == null || personDocumentIds.isEmpty()) {
                throw new IllegalArgumentException("Debe seleccionar al menos un documento");
            }

            // Delegar al servicio: crea la solicitud y los items asociados
            accessRequestService.createRequest(issuer.getIssuerId(), personId, purpose, personDocumentIds);

            // Luego de crear, redirigir al listado
            return "redirect:/issuer/access-requests";
        } catch (IllegalArgumentException ex) {
            // Mensaje claro en UI
            model.addAttribute("error", ex.getMessage());

            // Recargar la persona y sus documentos para volver a mostrar la pantalla completa
            Person person = personRepository.findById(personId).orElse(null);
            model.addAttribute("person", person);
            model.addAttribute("personDocuments",
                    person != null
                            ? personDocumentRepository.findApprovedByPersonIdWithFiles(personId)
                            : Collections.emptyList()
            );

            // Mantener valores para que la pantalla no se resetee (idType/idNumber)
            model.addAttribute("idType", person != null ? person.getIdType().name() : "");
            model.addAttribute("idNumber", person != null ? person.getIdNumber() : "");

            return "issuer/access-requests-new";
        }
    }

    /**
     * Permite visualizar un documento solicitado, únicamente cuando:
     * - La solicitud pertenece al emisor autenticado
     * - La solicitud está aprobada
     * - El documento (personDocumentId) está dentro de los items solicitados
     *
     * El servicio se encarga de validar esas reglas y cargar el Resource desde almacenamiento.
     *
     * @param auth             Autenticación del emisor
     * @param requestId        ID de la solicitud
     * @param personDocumentId ID del documento de la persona solicitado
     * @return ResponseEntity con el archivo en modo "inline" para visualización en navegador
     */
    @GetMapping("/issuer/access-requests/{requestId}/documents/{personDocumentId}/view")
    public ResponseEntity<Resource> viewDocument(
            Authentication auth,
            @PathVariable Long requestId,
            @PathVariable Long personDocumentId
    ) {
        IssuerPrincipal issuer = (IssuerPrincipal) auth.getPrincipal();

        // Carga el recurso solo si la solicitud está aprobada y corresponde al emisor
        Resource resource = accessRequestService.loadApprovedDocumentResource(
                issuer.getIssuerId(), requestId, personDocumentId
        );

        // Definir un nombre de archivo razonable si no viene en el Resource
        String filename = resource.getFilename() != null ? resource.getFilename() : "documento";

        // Inferir MediaType para que el navegador intente abrirlo (PDF/imagen, etc.)
        MediaType mediaType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);

        // Content-Disposition inline: se muestra en el navegador (no "attachment" forzado)
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(mediaType)
                .body(resource);
    }
}
