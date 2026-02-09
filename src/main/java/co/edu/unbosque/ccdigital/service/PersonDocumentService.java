package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.dto.PersonDocumentRequest;
import co.edu.unbosque.ccdigital.entity.*;
import co.edu.unbosque.ccdigital.repository.FileRecordRepository;
import co.edu.unbosque.ccdigital.repository.PersonDocumentRepository;
import co.edu.unbosque.ccdigital.repository.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Servicio de negocio para la gestión de documentos asociados a personas ({@link PersonDocument}).
 *
 * <p>Este servicio centraliza la lógica para crear y consultar {@code PersonDocument}, así como
 * almacenar y asociar archivos ({@link FileRecord}) en el sistema.</p>
 *
 * @author Danniel
 * @author Yeison
 * @since 2.0
 */
@Service
public class PersonDocumentService {

    private final PersonRepository personRepository;
    private final DocumentDefinitionService documentDefinitionService;
    private final PersonDocumentRepository personDocumentRepository;
    private final FileRecordRepository fileRecordRepository;
    private final FileStorageService fileStorageService;
    private final IssuingEntityService issuingEntityService;

    /**
     * Constructor con inyección de dependencias.
     *
     * @param personRepository repositorio de personas
     * @param documentDefinitionService servicio del catálogo de documentos
     * @param personDocumentRepository repositorio de documentos por persona
     * @param fileRecordRepository repositorio de registros de archivo
     * @param fileStorageService servicio de almacenamiento en disco
     * @param issuingEntityService servicio de emisores
     */
    public PersonDocumentService(PersonRepository personRepository,
                                 DocumentDefinitionService documentDefinitionService,
                                 PersonDocumentRepository personDocumentRepository,
                                 FileRecordRepository fileRecordRepository,
                                 FileStorageService fileStorageService,
                                 IssuingEntityService issuingEntityService) {
        this.personRepository = personRepository;
        this.documentDefinitionService = documentDefinitionService;
        this.personDocumentRepository = personDocumentRepository;
        this.fileRecordRepository = fileRecordRepository;
        this.fileStorageService = fileStorageService;
        this.issuingEntityService = issuingEntityService;
    }

    /**
     * Lista los documentos de una persona, trayendo relaciones (archivos, definición y emisor) con fetch join.
     *
     * @param personId id de la persona
     * @return lista de {@link PersonDocument} asociados a la persona
     */
    public List<PersonDocument> listByPerson(Long personId) {
        return personDocumentRepository.findByPersonIdWithFiles(personId);
    }

    /**
     * Obtiene un documento por su id, trayendo relaciones (archivos, definición y emisor) con fetch join.
     *
     * @param id id del {@link PersonDocument}
     * @return documento encontrado
     * @throws IllegalArgumentException si no existe
     */
    public PersonDocument getById(Long id) {
        return personDocumentRepository.findByIdWithFiles(id)
                .orElseThrow(() -> new IllegalArgumentException("PersonDocument no encontrado"));
    }

    /**
     * API: crea un {@link PersonDocument} a partir de un {@link PersonDocumentRequest} y opcionalmente
     * crea un {@link FileRecord} si el request ya trae {@code storagePath}.
     *
     * @param request request con la información del documento
     * @return {@link PersonDocument} persistido
     */
    @Transactional
    public PersonDocument create(PersonDocumentRequest request) {

        Person person = personRepository.findById(request.getPersonId())
                .orElseThrow(() -> new IllegalArgumentException("Persona no encontrada"));

        DocumentDefinition def = documentDefinitionService.findById(request.getDocumentId())
                .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado: " + request.getDocumentId()));

        // Resolver emisor (issuer) por nombre (issuingEntity del catálogo)
        IssuingEntity issuer = issuingEntityService.resolveEmitterByName(def.getIssuingEntity());

        PersonDocument pd = new PersonDocument();
        pd.setPerson(person);
        pd.setDocumentDefinition(def);

        pd.setStatus(request.getStatus() != null ? request.getStatus() : PersonDocumentStatus.VIGENTE);
        pd.setIssueDate(request.getIssueDate());
        pd.setExpiryDate(request.getExpiryDate());

        // NUEVO:
        pd.setIssuerEntity(issuer);
        pd.setReviewStatus(ReviewStatus.PENDING);

        // (por ahora) submitted/reviewed user quedan null hasta implementar login
        // pd.setSubmittedByEntityUserId(null);
        // pd.setReviewedByUserId(null);

        PersonDocument saved = personDocumentRepository.save(pd);

        // Asegurar relación emisor-documento (tabla entity_document_definitions)
        if (issuer != null) {
            issuingEntityService.ensureIssuerHasDocument(issuer, def.getId());
        }

        // Si viene storagePath, crear FileRecord básico
        if (request.getStoragePath() != null && !request.getStoragePath().isBlank()) {
            FileRecord fr = new FileRecord();
            fr.setPersonDocument(saved);
            fr.setDocument(def);
            fr.setStoragePath(request.getStoragePath());
            fr.setMimeType(request.getMimeType() != null ? request.getMimeType() : "application/octet-stream");
            fr.setHashSha256(request.getHashSha256());
            fr.setOriginalName(request.getStoragePath());
            fr.setByteSize(0L);
            fr.setStoredAs(FileStoredAs.PATH);
            fr.setVersion(1);
            fileRecordRepository.save(fr);

            // para que quede reflejado en el objeto en memoria
            saved.addFile(fr);
        }

        return saved;
    }

    /**
     * ISSUER: el emisor carga un documento para una persona y queda en revisión ({@link ReviewStatus#PENDING}).
     *
     * @param issuerId id del emisor que radica el documento
     * @param personId id de la persona destinataria
     * @param documentId id del tipo de documento (catálogo)
     * @param status estado funcional del documento (si es null, usa {@link PersonDocumentStatus#VIGENTE})
     * @param issueDate fecha de expedición
     * @param expiryDate fecha de vencimiento
     * @param file archivo cargado (multipart)
     * @return {@link PersonDocument} persistido con su {@link FileRecord} asociado
     * @throws IllegalArgumentException si faltan parámetros obligatorios o el documento no está permitido
     */
    @Transactional
    public PersonDocument uploadFromIssuer(Long issuerId,
                                           Long personId,
                                           Long documentId,
                                           PersonDocumentStatus status,
                                           java.time.LocalDate issueDate,
                                           java.time.LocalDate expiryDate,
                                           MultipartFile file) {

        if (issuerId == null) throw new IllegalArgumentException("issuerId es obligatorio");
        if (personId == null) throw new IllegalArgumentException("personId es obligatorio");
        if (documentId == null) throw new IllegalArgumentException("documentId es obligatorio");
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Archivo obligatorio");

        IssuingEntity issuer = issuingEntityService.getById(issuerId);

        // Validación: documento permitido para ese emisor
        boolean allowed = documentDefinitionService.findAllowedByIssuer(issuerId).stream()
                .anyMatch(d -> d.getId() != null && d.getId().equals(documentId));

        if (!allowed) {
            throw new IllegalArgumentException("Este emisor no tiene permitido cargar ese tipo de documento.");
        }

        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new IllegalArgumentException("Persona no encontrada"));

        DocumentDefinition def = documentDefinitionService.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado: " + documentId));

        // 1) Crear person_document
        PersonDocument pd = new PersonDocument();
        pd.setPerson(person);
        pd.setDocumentDefinition(def);
        pd.setStatus(status != null ? status : PersonDocumentStatus.VIGENTE);
        pd.setIssueDate(issueDate);
        pd.setExpiryDate(expiryDate);

        // Emisor + workflow
        pd.setIssuerEntity(issuer);
        pd.setReviewStatus(ReviewStatus.PENDING);
        pd.setSubmittedByEntityUserId(null); // login después

        PersonDocument savedPd = personDocumentRepository.save(pd);

        // 2) Guardar archivo en carpeta de la persona + hash
        FileStorageService.StoredFileInfo info = fileStorageService.storePersonFile(person, file);

        // 3) Crear FileRecord
        String mime = (file.getContentType() != null) ? file.getContentType() : "application/octet-stream";

        FileRecord fr = new FileRecord();
        fr.setDocument(def);
        fr.setPersonDocument(savedPd);
        fr.setOriginalName(info.getOriginalName());
        fr.setMimeType(mime);
        fr.setByteSize(info.getSize());
        fr.setHashSha256(info.getSha256());
        fr.setStoredAs(FileStoredAs.PATH);
        fr.setStoragePath(info.getRelativePath());
        fr.setVersion(1);

        fileRecordRepository.save(fr);
        savedPd.addFile(fr);

        // Asegurar relación issuer-document (por si acaso)
        issuingEntityService.ensureIssuerHasDocument(issuer, def.getId());

        return savedPd;
    }

    /**
     * ADMIN: carga un documento para una persona desde el módulo administrativo.
     *
     * @param personId id de la persona
     * @param documentId id del tipo de documento (catálogo)
     * @param status estado funcional del documento (si es null, usa {@link PersonDocumentStatus#VIGENTE})
     * @param issueDate fecha de expedición
     * @param expiryDate fecha de vencimiento
     * @param file archivo cargado (multipart)
     * @return {@link PersonDocument} persistido con su archivo asociado
     */
    @Transactional
    public PersonDocument uploadForPerson(Long personId,
                                          Long documentId,
                                          PersonDocumentStatus status,
                                          java.time.LocalDate issueDate,
                                          java.time.LocalDate expiryDate,
                                          MultipartFile file) {

        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new IllegalArgumentException("Persona no encontrada"));

        DocumentDefinition def = documentDefinitionService.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado: " + documentId));

        IssuingEntity issuer = issuingEntityService.resolveEmitterByName(def.getIssuingEntity());

        // 1) Crear person_document (con issuer + review status)
        PersonDocument pd = new PersonDocument();
        pd.setPerson(person);
        pd.setDocumentDefinition(def);

        pd.setStatus(status != null ? status : PersonDocumentStatus.VIGENTE);
        pd.setIssueDate(issueDate);
        pd.setExpiryDate(expiryDate);

        // NUEVO:
        pd.setIssuerEntity(issuer);
        pd.setReviewStatus(ReviewStatus.PENDING);

        PersonDocument savedPd = personDocumentRepository.save(pd);

        // 2) Guardar archivo en carpeta de la persona + hash
        FileStorageService.StoredFileInfo info = fileStorageService.storePersonFile(person, file);

        // 3) Crear file record
        String mime = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        FileRecord fr = new FileRecord();
        fr.setDocument(def);
        fr.setPersonDocument(savedPd);
        fr.setOriginalName(info.getOriginalName());
        fr.setMimeType(mime);
        fr.setByteSize(info.getSize());
        fr.setHashSha256(info.getSha256());
        fr.setStoredAs(FileStoredAs.PATH);
        fr.setStoragePath(info.getRelativePath());
        fr.setVersion(1);

        fileRecordRepository.save(fr);
        savedPd.addFile(fr);

        // Asegurar relación emisor-documento (tabla entity_document_definitions)
        if (issuer != null) {
            issuingEntityService.ensureIssuerHasDocument(issuer, def.getId());
        }

        return savedPd;
    }

    /**
     * ADMIN: actualiza el estado de revisión de un documento y guarda auditoría mínima.
     *
     * <p>Actualmente, el identificador del revisor ({@code reviewedByUserId}) se deja en {@code null}
     * hasta implementar el módulo de autenticación/autorización.</p>
     *
     * @param personDocumentId id del documento a revisar
     * @param status nuevo estado de revisión (si es null, se asigna {@link ReviewStatus#PENDING})
     * @param notes notas/observaciones del revisor (opcional)
     * @throws IllegalArgumentException si el documento no existe
     */
    @Transactional
    public void review(Long personDocumentId, ReviewStatus status, String notes) {

        PersonDocument pd = personDocumentRepository.findById(personDocumentId)
                .orElseThrow(() -> new IllegalArgumentException("PersonDocument no encontrado"));

        pd.setReviewStatus(status != null ? status : ReviewStatus.PENDING);
        pd.setReviewNotes((notes != null && !notes.isBlank()) ? notes.trim() : null);
        pd.setReviewedAt(LocalDateTime.now());
        pd.setReviewedByUserId(null); // login después

        personDocumentRepository.save(pd);
    }
}
