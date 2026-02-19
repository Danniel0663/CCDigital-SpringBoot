package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.entity.*;
import co.edu.unbosque.ccdigital.repository.AccessRequestRepository;
import co.edu.unbosque.ccdigital.repository.IssuingEntityRepository;
import co.edu.unbosque.ccdigital.repository.PersonDocumentRepository;
import co.edu.unbosque.ccdigital.repository.PersonRepository;
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

    private final AccessRequestRepository accessRequestRepository;
    private final PersonRepository personRepository;
    private final IssuingEntityRepository issuingEntityRepository;
    private final PersonDocumentRepository personDocumentRepository;
    private final FileStorageService fileStorageService;

    /**
     * Inyección de dependencias del servicio.
     *
     * @param accessRequestRepository repositorio de AccessRequest (consultas con detalle y persistencia)
     * @param personRepository repositorio de Person
     * @param issuingEntityRepository repositorio de entidades emisoras (IssuingEntity)
     * @param personDocumentRepository repositorio de PersonDocument (incluye consultas con archivos)
     * @param fileStorageService servicio para resolver y cargar archivos desde almacenamiento
     */
    public AccessRequestService(
            AccessRequestRepository accessRequestRepository,
            PersonRepository personRepository,
            IssuingEntityRepository issuingEntityRepository,
            PersonDocumentRepository personDocumentRepository,
            FileStorageService fileStorageService
    ) {
        this.accessRequestRepository = accessRequestRepository;
        this.personRepository = personRepository;
        this.issuingEntityRepository = issuingEntityRepository;
        this.personDocumentRepository = personDocumentRepository;
        this.fileStorageService = fileStorageService;
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
     * - Se crea AccessRequest con expiresAt a 7 días (regla actual).
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

        // Regla actual: expira en 7 días desde su creación
        request.setExpiresAt(LocalDateTime.now().plusDays(7));

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

        // Actualiza estado final y fecha de decisión
        request.setStatus(approve ? AccessRequestStatus.APROBADA : AccessRequestStatus.RECHAZADA);
        request.setDecidedAt(LocalDateTime.now());

        // Nota opcional de la decisión (se trimea para evitar espacios)
        if (decisionNote != null && !decisionNote.isBlank()) {
            request.setDecisionNote(decisionNote.trim());
        }

        accessRequestRepository.save(request);
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

        // Delegación a FileStorageService para resolver la ruta y devolver un Resource
        return fileStorageService.loadAsResource(latest);
    }
}
