package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.dto.FabricDocView;
import co.edu.unbosque.ccdigital.entity.*;
import co.edu.unbosque.ccdigital.repository.AccessRequestRepository;
import co.edu.unbosque.ccdigital.repository.IssuingEntityRepository;
import co.edu.unbosque.ccdigital.repository.PersonDocumentRepository;
import co.edu.unbosque.ccdigital.repository.PersonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Servicio de negocio para gestionar el flujo de "solicitudes de acceso" a documentos.
 *
 * Flujo general:
 * 1) Emisor crea una solicitud (PENDIENTE) para consultar 1..n documentos APROBADOS de una persona.
 * 2) Usuario (persona) aprueba o rechaza la solicitud.
 * 3) Si es APROBADA, el emisor puede visualizar los documentos solicitados (y solo esos).
 *
 * Consideraciones importantes:
 * - Se validan pertenencia de documentos a la persona, estado de revisión (ReviewStatus.APPROVED),
 *   y permisos básicos (persona decide solo sus solicitudes, emisor consulta solo sus solicitudes).
 * - Se maneja expiración (expiresAt) para invalidar solicitudes vencidas.
 * - Se usa FileStorageService para devolver un Resource del archivo del documento.
 */
@Service
public class AccessRequestService {
    private static final Logger log = LoggerFactory.getLogger(AccessRequestService.class);

    /**
     * Vista de trazabilidad blockchain para un documento autorizado en una solicitud.
     *
     * @param network nombre de la red blockchain
     * @param blockReference identificador de bloque/referencia on-chain
     * @param documentTitle título del documento
     * @param issuingEntity entidad emisora/origen reportado por Fabric
     * @param status estado del registro on-chain
     * @param createdAtHuman fecha de registro en formato legible
     * @param sizeHuman tamaño del archivo en formato legible
     * @param fileName nombre del archivo asociado
     * @param filePath ruta técnica reportada por Fabric
     */
    public record DocumentBlockchainTrace(
            String network,
            String blockReference,
            String documentTitle,
            String issuingEntity,
            String status,
            String createdAtHuman,
            String sizeHuman,
            String fileName,
            String filePath
    ) {}

    private final AccessRequestRepository accessRequestRepository;
    private final PersonRepository personRepository;
    private final IssuingEntityRepository issuingEntityRepository;
    private final PersonDocumentRepository personDocumentRepository;
    private final FileStorageService fileStorageService;
    private final ExternalToolsService externalToolsService;
    private final FabricLedgerCliService fabricLedgerCliService;

    /**
     * Inyección de dependencias del servicio.
     *
     * @param accessRequestRepository repositorio de AccessRequest (consultas con detalle y persistencia)
     * @param personRepository repositorio de Person
     * @param issuingEntityRepository repositorio de entidades emisoras (IssuingEntity)
     * @param personDocumentRepository repositorio de PersonDocument (incluye consultas con archivos)
     * @param fileStorageService servicio para resolver y cargar archivos desde almacenamiento
     * @param externalToolsService servicio de ejecución de scripts externos (sync a Fabric)
     * @param fabricLedgerCliService servicio de consulta de metadatos en Hyperledger Fabric
     */
    public AccessRequestService(
            AccessRequestRepository accessRequestRepository,
            PersonRepository personRepository,
            IssuingEntityRepository issuingEntityRepository,
            PersonDocumentRepository personDocumentRepository,
            FileStorageService fileStorageService,
            ExternalToolsService externalToolsService,
            FabricLedgerCliService fabricLedgerCliService
    ) {
        this.accessRequestRepository = accessRequestRepository;
        this.personRepository = personRepository;
        this.issuingEntityRepository = issuingEntityRepository;
        this.personDocumentRepository = personDocumentRepository;
        this.fileStorageService = fileStorageService;
        this.externalToolsService = externalToolsService;
        this.fabricLedgerCliService = fabricLedgerCliService;
    }

    /**
     * Crea una solicitud de acceso (estado inicial PENDIENTE) para consultar documentos aprobados de una persona.
     *
     * Validaciones:
     * - entityId, personId, purpose y personDocumentIds son obligatorios.
     * - La entidad emisora y la persona deben existir.
     * - Cada PersonDocument debe:
     *   1) Existir
     *   2) Pertenecer a la persona indicada
     *   3) Estar aprobado para consulta (ReviewStatus.APPROVED)
     *
     * Comportamiento:
     * - Se crea AccessRequest con expiresAt a 15 días (regla actual).
     * - Se crean AccessRequestItem por cada documento seleccionado.
     * - Se persiste la solicitud con cascade hacia items (por configuración en AccessRequest).
     *
     * @param entityId ID de la entidad emisora que solicita
     * @param personId ID de la persona dueña de los documentos
     * @param purpose motivo de la solicitud
     * @param personDocumentIds lista de IDs de PersonDocument solicitados
     * @return solicitud persistida
     * @throws IllegalArgumentException si alguna validación falla
     */
    @Transactional
    public AccessRequest createRequest(Long entityId, Long personId, String purpose, List<Long> personDocumentIds) {

        // Validación de argumentos básicos
        if (entityId == null) throw new IllegalArgumentException("entityId es requerido");
        if (personId == null) throw new IllegalArgumentException("personId es requerido");
        if (purpose == null || purpose.isBlank()) throw new IllegalArgumentException("purpose es requerido");
        if (personDocumentIds == null || personDocumentIds.isEmpty())
            throw new IllegalArgumentException("Debe seleccionar al menos un documento");

        // Carga y validación de entidad emisora
        IssuingEntity entity = issuingEntityRepository.findById(entityId)
                .orElseThrow(() -> new IllegalArgumentException("Entidad no encontrada"));

        // Carga y validación de persona
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new IllegalArgumentException("Persona no encontrada"));

        // Construcción de la solicitud principal
        AccessRequest request = new AccessRequest();
        request.setEntity(entity);
        request.setPerson(person);
        request.setPurpose(purpose.trim());
        request.setStatus(AccessRequestStatus.PENDIENTE);

        // Regla actual: expira en 15 días desde su creación (ampliado desde 7 días).
        request.setExpiresAt(LocalDateTime.now().plusDays(15));

        // Construcción de items solicitados
        List<AccessRequestItem> items = new ArrayList<>();

        for (Long pdId : personDocumentIds) {

            // Se carga el PersonDocument con sus relaciones necesarias (incluye archivos)
            PersonDocument pd = personDocumentRepository.findByIdWithFiles(pdId)
                    .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado: " + pdId));

            // Validación: el documento debe pertenecer a la persona objetivo de la solicitud
            if (!Objects.equals(pd.getPerson().getId(), personId)) {
                throw new IllegalArgumentException("El documento no pertenece a la persona");
            }

            // Validación: solo se pueden solicitar documentos ya aprobados por Gobierno/Admin
            if (pd.getReviewStatus() != ReviewStatus.APPROVED) {
                throw new IllegalArgumentException("El documento aún no está aprobado para consulta");
            }

            // Creación del item (detalle)
            AccessRequestItem item = new AccessRequestItem();
            item.setAccessRequest(request);   // Enlaza al padre (necesario para mappedBy y cascade)
            item.setPersonDocument(pd);
            items.add(item);
        }

        // Se asigna la lista de items al request y se persiste
        request.setItems(items);
        return accessRequestRepository.save(request);
    }

    /**
     * Lista solicitudes de acceso asociadas a una persona, incluyendo detalle para UI.
     *
     * @param personId ID de la persona
     * @return solicitudes con detalle (entity, items, títulos, etc.)
     */
    @Transactional(readOnly = true)
    public List<AccessRequest> listForPerson(Long personId) {
        return accessRequestRepository.findForPersonWithDetails(personId);
    }

    /**
     * Lista solicitudes creadas por una entidad emisora, incluyendo detalle para UI.
     *
     * @param entityId ID de la entidad emisora
     * @return solicitudes con detalle (person, items, títulos, etc.)
     */
    @Transactional(readOnly = true)
    public List<AccessRequest> listForEntity(Long entityId) {
        return accessRequestRepository.findForEntityWithDetails(entityId);
    }

    /**
     * Obtiene una solicitud por ID con su detalle completo.
     *
     * @param requestId ID de la solicitud
     * @return solicitud con relaciones cargadas
     * @throws IllegalArgumentException si no existe
     */
    @Transactional(readOnly = true)
    public AccessRequest getWithDetails(Long requestId) {
        return accessRequestRepository.findByIdWithDetails(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada"));
    }

    /**
     * Permite que el usuario (persona) apruebe o rechace una solicitud pendiente.
     *
     * Validaciones:
     * - La solicitud debe existir.
     * - Debe pertenecer a la persona que decide.
     * - Debe estar en estado PENDIENTE.
     * - Si está vencida, se marca como EXPIRADA y se aborta la operación.
     *
     * Comportamiento:
     * - Actualiza status a APROBADA o RECHAZADA.
     * - Registra decidedAt.
     * - Si decisionNote viene con contenido, la guarda.
     * - Si la decisión es aprobar:
     *   1) sincroniza la persona en Hyperledger Fabric
     *   2) valida que los documentos solicitados queden visibles en Fabric
     *   3) solo entonces persiste la APROBACIÓN
     *
     * @param requestId ID de la solicitud
     * @param personId ID de la persona que decide
     * @param approve true para aprobar, false para rechazar
     * @param decisionNote nota opcional
     * @throws IllegalArgumentException si falla la autorización/estado/reglas
     */
    @Transactional
    public void decide(Long requestId, Long personId, boolean approve, String decisionNote) {

        AccessRequest request = accessRequestRepository.findByIdWithDetails(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada"));

        // Autorización: solo la persona dueña puede decidir
        if (!Objects.equals(request.getPerson().getId(), personId)) {
            log.warn("Intento no autorizado de decisión de solicitud. requestId={}, personIdSolicitante={}, personIdSesion={}",
                    requestId, request.getPerson().getId(), personId);
            throw new IllegalArgumentException("No autorizado para decidir esta solicitud");
        }

        // Regla: solo se puede decidir si está pendiente
        if (request.getStatus() != AccessRequestStatus.PENDIENTE) {
            throw new IllegalArgumentException("La solicitud ya fue decidida");
        }

        // Regla: si está expirada, se marca y se aborta
        if (request.getExpiresAt() != null && request.getExpiresAt().isBefore(LocalDateTime.now())) {
            request.setStatus(AccessRequestStatus.EXPIRADA);
            request.setDecidedAt(LocalDateTime.now());
            accessRequestRepository.save(request);
            throw new IllegalArgumentException("La solicitud se encuentra expirada");
        }

        // Si aprueba, primero sincroniza y valida trazabilidad en Fabric.
        // Si falla, NO se aprueba la solicitud (se mantiene en PENDIENTE).
        if (approve) {
            syncApprovedPersonDocumentsToFabric(request);
            validateApprovedItemsAreInFabric(request);
        }

        // Actualiza estado final y fecha de decisión
        request.setStatus(approve ? AccessRequestStatus.APROBADA : AccessRequestStatus.RECHAZADA);
        request.setDecidedAt(LocalDateTime.now());

        // Nota opcional de la decisión (se trimea para evitar espacios)
        if (decisionNote != null && !decisionNote.isBlank()) {
            request.setDecisionNote(decisionNote.trim());
        }

        accessRequestRepository.save(request);
        log.info("Solicitud decidida. requestId={}, personId={}, status={}", requestId, personId, request.getStatus());
    }

    /**
     * Carga un recurso (archivo) asociado a un PersonDocument solicitado, validando:
     * - Que la solicitud exista y pertenezca a la entidad emisora autenticada.
     * - Que la solicitud esté APROBADA.
     * - Que no esté expirada.
     * - Que el personDocumentId pertenezca a los items solicitados en esa solicitud.
     *
     * Importante:
     * - Esta operación NO devuelve información si la solicitud no está aprobada,
     *   evitando que el emisor consulte documentos por fuera del consentimiento.
     * - Antes de entregar el archivo, se valida trazabilidad en Fabric para asegurar
     *   que el documento consultado se encuentre registrado on-chain.
     *
     * Selección de archivo:
     * - Si el PersonDocument tiene múltiples archivos (versiones), se elige el de mayor versión.
     *
     * @param entityId ID de la entidad emisora
     * @param requestId ID de la solicitud
     * @param personDocumentId ID del documento solicitado
     * @return Resource listo para servir por HTTP (inline/download depende del controller)
     * @throws IllegalArgumentException si falla alguna validación
     */
    @Transactional(readOnly = true)
    public Resource loadApprovedDocumentResource(Long entityId, Long requestId, Long personDocumentId) {

        AccessRequest request = accessRequestRepository.findByIdWithDetails(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada"));

        // Autorización: solo el emisor dueño de la solicitud puede usarla
        if (!Objects.equals(request.getEntity().getId(), entityId)) {
            log.warn("Intento no autorizado de consulta de documento. requestId={}, entityIdSolicitud={}, entityIdSesion={}, personDocumentId={}",
                    requestId, request.getEntity().getId(), entityId, personDocumentId);
            throw new IllegalArgumentException("No autorizado para consultar esta solicitud");
        }

        // Regla: solo solicitudes aprobadas permiten acceder a documentos
        if (request.getStatus() != AccessRequestStatus.APROBADA) {
            throw new IllegalArgumentException("La solicitud no está aprobada");
        }

        // Regla: si está expirada, no se permite acceso
        if (request.getExpiresAt() != null && request.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("La solicitud se encuentra expirada");
        }

        // Validación: el documento debe estar dentro de los items solicitados
        boolean requested = request.getItems().stream()
                .anyMatch(i -> Objects.equals(i.getPersonDocument().getId(), personDocumentId));
        if (!requested) {
            throw new IllegalArgumentException("El documento no pertenece a la solicitud");
        }

        // Carga del documento con archivos
        PersonDocument pd = personDocumentRepository.findByIdWithFiles(personDocumentId)
                .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado"));

        // Validación: debe existir al menos un archivo asociado
        if (pd.getFiles() == null || pd.getFiles().isEmpty()) {
            throw new IllegalArgumentException("El documento no tiene archivo asociado");
        }

        // Selecciona el archivo con mayor versión (si version es null, se trata como 0)
        FileRecord latest = pd.getFiles().stream()
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(fr -> fr.getVersion() != null ? fr.getVersion() : 0))
                .orElseThrow(() -> new IllegalArgumentException("El documento no tiene archivos válidos"));

        // Seguridad/trazabilidad: antes de entregar el archivo al emisor, se exige
        // que el documento exista en la consulta on-chain de Fabric para la persona.
        ensureDocumentPresentInFabric(request.getPerson(), pd, latest);
        log.info("Consulta autorizada de documento. requestId={}, entityId={}, personId={}, personDocumentId={}, fileId={}",
                requestId, entityId, request.getPerson().getId(), personDocumentId, latest.getId());

        // Delegación a FileStorageService para resolver la ruta y devolver un Resource
        return fileStorageService.loadAsResource(latest);
    }

    /**
     * Obtiene el detalle de trazabilidad blockchain de un documento autorizado de una solicitud.
     *
     * <p>Aplica las mismas validaciones de seguridad y negocio del flujo de visualización:
     * entidad dueña de la solicitud, estado aprobado, no expiración y pertenencia del documento.</p>
     *
     * @param entityId entidad emisora autenticada
     * @param requestId solicitud aprobada
     * @param personDocumentId documento solicitado
     * @return metadatos del bloque on-chain correspondiente al documento
     */
    @Transactional(readOnly = true)
    public DocumentBlockchainTrace loadApprovedDocumentBlockchainTrace(Long entityId,
                                                                       Long requestId,
                                                                       Long personDocumentId) {
        AccessRequest request = accessRequestRepository.findByIdWithDetails(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada"));

        if (!Objects.equals(request.getEntity().getId(), entityId)) {
            log.warn("Intento no autorizado de trazabilidad de documento. requestId={}, entityIdSolicitud={}, entityIdSesion={}, personDocumentId={}",
                    requestId, request.getEntity().getId(), entityId, personDocumentId);
            throw new IllegalArgumentException("No autorizado para consultar esta solicitud");
        }

        if (request.getStatus() != AccessRequestStatus.APROBADA) {
            throw new IllegalArgumentException("La solicitud no está aprobada");
        }

        if (request.getExpiresAt() != null && request.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("La solicitud se encuentra expirada");
        }

        boolean requested = request.getItems().stream()
                .anyMatch(i -> Objects.equals(i.getPersonDocument().getId(), personDocumentId));
        if (!requested) {
            throw new IllegalArgumentException("El documento no pertenece a la solicitud");
        }

        PersonDocument pd = personDocumentRepository.findByIdWithFiles(personDocumentId)
                .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado"));

        FileRecord latest = findLatestFile(pd);
        List<FabricDocView> fabricDocs = loadFabricDocsForPerson(request.getPerson());
        FabricDocView fabricDoc = findMatchingFabricDoc(fabricDocs, pd, latest)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se encontró la referencia del documento en Fabric para esta solicitud."
                ));

        return new DocumentBlockchainTrace(
                "Hyperledger Fabric",
                safeText(fabricDoc.docId(), "Sin referencia"),
                safeText(fabricDoc.title(), "Documento sin título"),
                safeText(fabricDoc.issuingEntity(), "Entidad no identificada"),
                safeText(fabricDoc.status(), "Registrado"),
                safeText(fabricDoc.createdAtHuman(), "No disponible"),
                safeText(fabricDoc.sizeHuman(), "No disponible"),
                safeText(fabricDoc.fileName(), "documento"),
                safeText(fabricDoc.filePath(), "No disponible")
        );
    }

    /**
     * Sincroniza en Fabric los documentos de la persona asociada a una solicitud aprobada.
     *
     * <p>Se invoca únicamente cuando el usuario aprueba una solicitud. Si la sincronización falla
     * (script no configurado, error de ejecución o timeout), se lanza una excepción de negocio para
     * evitar que la solicitud quede marcada como aprobada sin trazabilidad en Fabric.</p>
     *
     * @param request solicitud que se está aprobando
     */
    private void syncApprovedPersonDocumentsToFabric(AccessRequest request) {
        Person person = request.getPerson();
        String idType = person.getIdType() != null ? person.getIdType().name() : null;
        String idNumber = person.getIdNumber();

        ExternalToolsService.ExecResult result = externalToolsService.runFabricSyncPerson(idType, idNumber);
        if (!result.isOk()) {
            throw new IllegalArgumentException(
                    "No se pudo registrar la aprobación en Fabric. Intente nuevamente. Detalle: "
                            + firstNonBlankLine(result.getStderr(), "Error de sincronización con Fabric")
            );
        }
    }

    /**
     * Valida que todos los documentos solicitados en una aprobación estén visibles en la consulta de Fabric.
     *
     * <p>La validación se hace por coincidencia de ruta de archivo (preferido) y por título como respaldo,
     * para reducir falsos negativos cuando Fabric expone la ruta absoluta y la BD almacena ruta relativa.</p>
     *
     * @param request solicitud aprobada (aún no persistida como APROBADA)
     */
    private void validateApprovedItemsAreInFabric(AccessRequest request) {
        Person person = request.getPerson();
        List<FabricDocView> fabricDocs = loadFabricDocsForPerson(person);

        for (AccessRequestItem item : request.getItems()) {
            Long personDocumentId = item.getPersonDocument() != null ? item.getPersonDocument().getId() : null;
            if (personDocumentId == null) {
                throw new IllegalArgumentException("La solicitud contiene un documento inválido");
            }

            PersonDocument pd = personDocumentRepository.findByIdWithFiles(personDocumentId)
                    .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado: " + personDocumentId));

            FileRecord latest = findLatestFile(pd);
            if (findMatchingFabricDoc(fabricDocs, pd, latest).isEmpty()) {
                String title = pd.getDocumentDefinition() != null ? pd.getDocumentDefinition().getTitle() : "Documento";
                throw new IllegalArgumentException(
                        "No se pudo validar en Fabric el documento '" + title + "'. Intente nuevamente."
                );
            }
        }
    }

    /**
     * Exige que un documento específico exista en Fabric antes de entregarlo al emisor.
     *
     * @param person persona propietaria del documento
     * @param pd documento solicitado
     * @param latest archivo seleccionado para visualización
     */
    private void ensureDocumentPresentInFabric(Person person, PersonDocument pd, FileRecord latest) {
        List<FabricDocView> fabricDocs = loadFabricDocsForPerson(person);
        if (findMatchingFabricDoc(fabricDocs, pd, latest).isEmpty()) {
            throw new IllegalArgumentException(
                    "El documento no está disponible en Fabric para esta persona. Solicite una nueva aprobación."
            );
        }
    }

    /**
     * Consulta documentos de una persona en Fabric y traduce errores técnicos a mensajes de negocio.
     *
     * @param person persona propietaria
     * @return lista de documentos visibles en Fabric
     */
    private List<FabricDocView> loadFabricDocsForPerson(Person person) {
        try {
            String idType = person.getIdType() != null ? person.getIdType().name() : "";
            String idNumber = person.getIdNumber() != null ? person.getIdNumber() : "";
            return fabricLedgerCliService.listDocsView(idType, idNumber);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(
                    "No fue posible consultar la trazabilidad en Fabric. Intente nuevamente."
            );
        }
    }

    /**
     * Selecciona el archivo de mayor versión de un documento.
     *
     * @param pd documento de persona con archivos cargados
     * @return archivo más reciente
     */
    private FileRecord findLatestFile(PersonDocument pd) {
        if (pd.getFiles() == null || pd.getFiles().isEmpty()) {
            throw new IllegalArgumentException("El documento no tiene archivo asociado");
        }
        return pd.getFiles().stream()
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(fr -> fr.getVersion() != null ? fr.getVersion() : 0))
                .orElseThrow(() -> new IllegalArgumentException("El documento no tiene archivos válidos"));
    }

    /**
     * Busca un documento equivalente en la lista retornada por Fabric.
     *
     * <p>Orden de coincidencia:</p>
     * <ol>
     *   <li>Ruta de archivo (exacta o sufijo cuando Fabric expone ruta absoluta).</li>
     *   <li>Título del documento (respaldo).</li>
     * </ol>
     *
     * @param fabricDocs documentos visibles en Fabric
     * @param pd documento solicitado en BD
     * @param latest archivo local asociado
     * @return coincidencia opcional
     */
    private Optional<FabricDocView> findMatchingFabricDoc(List<FabricDocView> fabricDocs, PersonDocument pd, FileRecord latest) {
        String dbRelativePath = normalizePath(latest.getStoragePath());
        String title = pd.getDocumentDefinition() != null ? pd.getDocumentDefinition().getTitle() : null;

        return fabricDocs.stream()
                .filter(Objects::nonNull)
                .filter(doc -> matchesFilePath(doc.filePath(), dbRelativePath) || matchesTitle(doc.title(), title))
                .findFirst();
    }

    /**
     * Compara rutas considerando que BD suele guardar rutas relativas y Fabric puede retornar absolutas.
     *
     * @param fabricPath ruta reportada por Fabric
     * @param dbRelativePath ruta relativa persistida en BD
     * @return {@code true} si coinciden bajo las reglas de normalización/sufijo
     */
    private boolean matchesFilePath(String fabricPath, String dbRelativePath) {
        if (fabricPath == null || fabricPath.isBlank() || dbRelativePath == null || dbRelativePath.isBlank()) {
            return false;
        }
        String fabricNorm = normalizePath(fabricPath);
        String dbNorm = normalizePath(dbRelativePath);
        return fabricNorm.equals(dbNorm)
                || fabricNorm.endsWith("/" + dbNorm)
                || fabricNorm.endsWith(dbNorm);
    }

    /**
     * Compara títulos de forma tolerante para una coincidencia de respaldo.
     *
     * @param fabricTitle título reportado por Fabric
     * @param dbTitle título del documento en BD
     * @return {@code true} si coinciden ignorando mayúsculas/minúsculas
     */
    private boolean matchesTitle(String fabricTitle, String dbTitle) {
        if (fabricTitle == null || dbTitle == null) return false;
        String a = fabricTitle.trim();
        String b = dbTitle.trim();
        return !a.isEmpty() && !b.isEmpty() && a.equalsIgnoreCase(b);
    }

    /**
     * Normaliza una ruta a formato con separador {@code /} para comparaciones.
     *
     * @param value ruta original
     * @return ruta normalizada
     */
    private String normalizePath(String value) {
        return value == null ? "" : value.trim().replace('\\', '/');
    }

    /**
     * Extrae la primera línea no vacía de una salida de consola para mostrar un mensaje compacto.
     *
     * @param text texto completo (stderr/stdout)
     * @param fallback valor por defecto si no hay líneas útiles
     * @return primera línea no vacía
     */
    private String firstNonBlankLine(String text, String fallback) {
        if (text == null || text.isBlank()) return fallback;
        return Arrays.stream(text.split("\\R"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .findFirst()
                .orElse(fallback);
    }

    /**
     * Normaliza un texto y aplica valor de respaldo cuando sea nulo o vacío.
     */
    private String safeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
