package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.entity.IdType;
import co.edu.unbosque.ccdigital.entity.Person;
import co.edu.unbosque.ccdigital.entity.PersonDocument;
import co.edu.unbosque.ccdigital.security.IssuerPrincipal;
import co.edu.unbosque.ccdigital.service.AccessRequestService;
import co.edu.unbosque.ccdigital.service.SignedUrlService;
import co.edu.unbosque.ccdigital.repository.PersonDocumentRepository;
import co.edu.unbosque.ccdigital.repository.PersonRepository;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

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
    private final SignedUrlService signedUrlService;

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
            AccessRequestService accessRequestService,
            SignedUrlService signedUrlService
    ) {
        this.personRepository = personRepository;
        this.personDocumentRepository = personDocumentRepository;
        this.accessRequestService = accessRequestService;
        this.signedUrlService = signedUrlService;
    }

    /**
     * Muestra el listado de solicitudes creadas por el emisor autenticado.
     *
     * @param auth  Autenticación de Spring Security (contiene el principal del emisor)
     * @param error mensaje opcional para UI (ej. solicitud expirada al intentar ver un documento)
     * @param model Modelo para la vista
     * @return Nombre de la vista Thymeleaf con el listado
     */
    @GetMapping("/issuer/access-requests")
    public String list(Authentication auth,
                       @RequestParam(required = false) String error,
                       Model model) {
        // Se obtiene el emisor autenticado desde el principal de seguridad
        IssuerPrincipal issuer = (IssuerPrincipal) auth.getPrincipal();

        // Avisos de navegación segura (por ejemplo, si un documento no puede abrirse por expiración).
        if (error != null && !error.isBlank()) {
            model.addAttribute("error", error);
        }

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
        return populateNewRequestView(idType, idNumber, model);
    }

    /**
     * Recibe el post del formulario "Buscar persona" y procesa la búsqueda en servidor.
     *
     * <p>Se evita poner tipo/número de identificación en la URL para reducir exposición en historial,
     * logs y capturas. El resultado se renderiza directamente en la misma vista.</p>
     *
     * @param idType   Tipo de identificación
     * @param idNumber Número de identificación
     * @param model modelo de la vista
     * @return Vista de nueva solicitud con resultado de búsqueda
     */
    @PostMapping("/issuer/access-requests/search")
    public String search(@RequestParam String idType, @RequestParam String idNumber, Model model) {
        return populateNewRequestView(idType, idNumber, model);
    }

    /**
     * Carga la vista de creación de solicitud resolviendo opcionalmente la persona buscada.
     *
     * @param idType tipo de identificación ingresado
     * @param idNumber número de identificación ingresado
     * @param model modelo de la vista
     * @return vista "issuer/access-requests-new" con persona/documentos o mensajes de error
     */
    private String populateNewRequestView(String idType, String idNumber, Model model) {
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
            IdType enumIdType = IdType.valueOf(idType);
            Person person = personRepository.findByIdTypeAndIdNumber(enumIdType, idNumber).orElse(null);

            if (person == null) {
                model.addAttribute("error", "No se encontró la persona con la identificación indicada");
                model.addAttribute("person", null);
                model.addAttribute("personDocuments", Collections.emptyList());
                return "issuer/access-requests-new";
            }

            List<PersonDocument> docs = personDocumentRepository.findApprovedByPersonIdWithFiles(person.getId());
            model.addAttribute("person", person);
            model.addAttribute("personDocuments", docs);
            return "issuer/access-requests-new";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", "Tipo de identificación no válido");
            model.addAttribute("person", null);
            model.addAttribute("personDocuments", Collections.emptyList());
            return "issuer/access-requests-new";
        }
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
            Person person = personId == null ? null : personRepository.findById(personId).orElse(null);
            model.addAttribute("person", person);
            model.addAttribute("personDocuments",
                    person != null && personId != null
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
     * Si la validación de negocio falla (ej. solicitud expirada), se redirige al listado con
     * mensaje amigable en vez de exponer un error técnico en pantalla.
     *
     * @param auth             Autenticación del emisor
     * @param requestId        ID de la solicitud
     * @param personDocumentId ID del documento de la persona solicitado
     * @param exp              expiración de la URL firmada (epoch seconds)
     * @param sig              firma HMAC de la URL
     * @return ResponseEntity con el archivo en modo "inline" para visualización en navegador
     */
    @GetMapping("/issuer/access-requests/{requestId}/documents/{personDocumentId}/view")
    public ResponseEntity<?> viewDocument(
            Authentication auth,
            @PathVariable Long requestId,
            @PathVariable Long personDocumentId,
            @RequestParam("exp") Long exp,
            @RequestParam("sig") String sig
    ) {
        return serveDocument(auth, requestId, personDocumentId, exp, sig, false);
    }

    /**
     * Valida (sin transferir archivo) si un documento puede abrirse en vista previa.
     *
     * <p>Se usa desde la UI del emisor antes de cargar el iframe para evitar que una solicitud
     * vencida termine renderizando una página completa dentro del visor.</p>
     *
     * @param auth autenticación del emisor
     * @param requestId ID de la solicitud
     * @param personDocumentId ID del documento solicitado
     * @param exp expiración de URL firmada (epoch seconds)
     * @param sig firma HMAC de la URL
     * @return 200 si puede abrirse; 410 con encabezado de error si no aplica
     */
    @RequestMapping(
            value = "/issuer/access-requests/{requestId}/documents/{personDocumentId}/view",
            method = RequestMethod.HEAD
    )
    public ResponseEntity<Void> checkViewDocument(
            Authentication auth,
            @PathVariable Long requestId,
            @PathVariable Long personDocumentId,
            @RequestParam("exp") Long exp,
            @RequestParam("sig") String sig
    ) {
        IssuerPrincipal issuer = (IssuerPrincipal) auth.getPrincipal();
        signedUrlService.validateIssuerDocumentView(requestId, personDocumentId, exp, sig);
        try {
            accessRequestService.loadApprovedDocumentResource(issuer.getIssuerId(), requestId, personDocumentId);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .build();
        } catch (IllegalArgumentException ex) {
            String encodedMessage = java.net.URLEncoder.encode(ex.getMessage(), StandardCharsets.UTF_8);
            return ResponseEntity.status(HttpStatus.GONE)
                    .cacheControl(CacheControl.noStore())
                    .header("X-CCDigital-Error", encodedMessage)
                    .build();
        }
    }

    /**
     * Permite descargar un documento solicitado cuando la solicitud ya fue aprobada.
     *
     * @param auth autenticación del emisor
     * @param requestId ID de la solicitud
     * @param personDocumentId ID del documento solicitado
     * @param exp expiración de URL firmada (epoch seconds)
     * @param sig firma HMAC de la URL
     * @return respuesta con archivo en modo attachment
     */
    @GetMapping("/issuer/access-requests/{requestId}/documents/{personDocumentId}/download")
    public ResponseEntity<?> downloadDocument(
            Authentication auth,
            @PathVariable Long requestId,
            @PathVariable Long personDocumentId,
            @RequestParam("exp") Long exp,
            @RequestParam("sig") String sig
    ) {
        return serveDocument(auth, requestId, personDocumentId, exp, sig, true);
    }

    /**
     * Retorna metadatos de trazabilidad blockchain del documento autorizado (JSON para modal interactivo).
     *
     * <p>Usa URL firmada y validaciones de negocio del servicio para asegurar que solo el emisor
     * propietario de la solicitud aprobada pueda consultar la referencia de bloque.</p>
     *
     * @param auth autenticación del emisor
     * @param requestId id de la solicitud
     * @param personDocumentId id del documento solicitado
     * @param exp expiración de URL firmada (epoch seconds)
     * @param sig firma HMAC de la URL
     * @return respuesta JSON con detalle de bloque o error de negocio controlado
     */
    @GetMapping("/issuer/access-requests/{requestId}/documents/{personDocumentId}/block")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> blockDetail(
            Authentication auth,
            @PathVariable Long requestId,
            @PathVariable Long personDocumentId,
            @RequestParam("exp") Long exp,
            @RequestParam("sig") String sig
    ) {
        IssuerPrincipal issuer = (IssuerPrincipal) auth.getPrincipal();
        signedUrlService.validateIssuerDocumentBlock(requestId, personDocumentId, exp, sig);

        try {
            AccessRequestService.DocumentBlockchainTrace trace = accessRequestService
                    .loadApprovedDocumentBlockchainTrace(issuer.getIssuerId(), requestId, personDocumentId);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("requestId", requestId);
            payload.put("personDocumentId", personDocumentId);
            payload.put("network", trace.network());
            payload.put("blockReference", trace.blockReference());
            payload.put("documentTitle", trace.documentTitle());
            payload.put("issuingEntity", trace.issuingEntity());
            payload.put("status", trace.status());
            payload.put("createdAtHuman", trace.createdAtHuman());
            payload.put("sizeHuman", trace.sizeHuman());
            payload.put("fileName", trace.fileName());
            payload.put("filePath", trace.filePath());
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(payload);
        } catch (IllegalArgumentException ex) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("error", ex.getMessage());
            payload.put("requestId", requestId);
            payload.put("personDocumentId", personDocumentId);
            return ResponseEntity.status(HttpStatus.GONE)
                    .cacheControl(CacheControl.noStore())
                    .body(payload);
        }
    }

    /**
     * Resuelve y sirve el documento autorizado en modo vista o descarga.
     */
    private ResponseEntity<?> serveDocument(Authentication auth,
                                           Long requestId,
                                           Long personDocumentId,
                                           Long exp,
                                           String sig,
                                           boolean asAttachment) {
        IssuerPrincipal issuer = (IssuerPrincipal) auth.getPrincipal();
        if (asAttachment) {
            signedUrlService.validateIssuerDocumentDownload(requestId, personDocumentId, exp, sig);
        } else {
            signedUrlService.validateIssuerDocumentView(requestId, personDocumentId, exp, sig);
        }

        final Resource resource;
        try {
            // Carga el recurso solo si la solicitud está aprobada y corresponde al emisor.
            resource = accessRequestService.loadApprovedDocumentResource(
                    issuer.getIssuerId(), requestId, personDocumentId
            );
        } catch (IllegalArgumentException ex) {
            // Redirección con mensaje de negocio para evitar stacktrace/500 en la UI del emisor.
            String msg = java.net.URLEncoder.encode(ex.getMessage(), StandardCharsets.UTF_8);
            return ResponseEntity.status(302)
                    .header(HttpHeaders.LOCATION, "/issuer/access-requests?error=" + msg)
                    .build();
        }

        String filename = resource.getFilename() != null ? resource.getFilename() : "documento";
        MediaType mediaType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
        if (mediaType == null) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        MediaType responseMediaType = Objects.requireNonNull(mediaType);
        String dispositionType = asAttachment ? "attachment" : "inline";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, dispositionType + "; filename=\"" + filename + "\"")
                .contentType(responseMediaType)
                .body(resource);
    }
}
