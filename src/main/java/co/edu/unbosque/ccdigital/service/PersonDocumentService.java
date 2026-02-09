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
 * Servicio de dominio para gestión de documentos asociados a personas.
 *
 * <p>Administra la creación de {@link PersonDocument}, la carga de archivos mediante {@link FileRecord}
 * y el flujo de revisión administrativa.</p>
 */
@Service
public class PersonDocumentService {

    private final PersonRepository personRepository;
    private final DocumentDefinitionService documentDefinitionService;
    private final PersonDocumentRepository personDocumentRepository;
    private final FileRecordRepository fileRecordRepository;
    private final FileStorageService fileStorageService;
    private final IssuingEntityService issuingEntityService;

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
     * Lista documentos de una persona con archivos y relaciones necesarias precargadas.
     *
     * @param personId id de la persona
     * @return lista de documentos
     */
    public List<PersonDocument> listByPerson(Long personId) {
        return personDocumentRepository.findByPersonIdWithFiles(personId);
    }

    /**
     * Obtiene un documento asociado a persona por id con relaciones precargadas.
     *
     * @param id id del documento de persona
     * @return documento
     * @throws IllegalArgumentException si no existe
     */
    public PersonDocument getById(Long id) {
        return personDocumentRepository.findByIdWithFiles(id)
                .orElseThrow(() -> new IllegalArgumentException("PersonDocument no encontrado"));
    }

    /**
     * Crea un {@link PersonDocument} desde API y, opcionalmente, crea un {@link FileRecord} cuando
     * el request ya contiene un {@code storagePath}.
     *
     * <p>Durante la creación:
     * <ul>
     *   <li>Se resuelve el emisor con base en {@code def.issuingEntity}</li>
     *   <li>Se asigna {@link ReviewStatus#PENDING} por defecto</li>
     *   <li>Se asegura la relación emisor-documento en {@code entity_document_definitions}</li>
     * </ul>
     * </p>
     *
     * @param request datos de creación del documento
     * @return documento creado
     */
    @Transactional
    public PersonDocument create(PersonDocumentRequest request) {

        Person person = personRepository.findById(request.getPersonId())
                .orElseThrow(() -> new IllegalArgumentException("Persona no encontrada"));

        DocumentDefinition def = documentDefinitionService.findById(request.getDocumentId())
                .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado: " + request.getDocumentId()));

        IssuingEntity issuer = issuingEntityService.resolveEmitterByName(def.getIssuingEntity());

        PersonDocument pd = new PersonDocument();
        pd.setPerson(person);
        pd.setDocumentDefinition(def);

        pd.setStatus(request.getStatus() != null ? request.getStatus() : PersonDocumentStatus.VIGENTE);
        pd.setIssueDate(request.getIssueDate());
        pd.setExpiryDate(request.getExpiryDate());

        pd.setIssuerEntity(issuer);
        pd.setReviewStatus(ReviewStatus.PENDING);

        PersonDocument saved = personDocumentRepository.save(pd);

        if (issuer != null) {
            issuingEntityService.ensureIssuerHasDocument(issuer, def.getId());
        }

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

            saved.addFile(fr);
        }

        return saved;
    }

    /**
     * Flujo emisor: el emisor carga un documento para una persona y queda en revisión.
     *
     * <p>Valida que el tipo de documento esté permitido para el emisor. Almacena el archivo en disco,
     * calcula hash SHA-256 y crea el {@link FileRecord} correspondiente.</p>
     *
     * @param issuerId id del emisor
     * @param personId id de la persona
     * @param documentId id del documento del catálogo
     * @param status estado funcional del documento
     * @param issueDate fecha de emisión
     * @param expiryDate fecha de vencimiento
     * @param file archivo cargado
     * @return documento creado con su archivo asociado
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

        boolean allowed = documentDefinitionService.findAllowedByIssuer(issuerId).stream()
                .anyMatch(d -> d.getId() != null && d.getId().equals(documentId));

        if (!allowed) {
            throw new IllegalArgumentException("Este emisor no tiene permitido cargar ese tipo de documento.");
        }

        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new IllegalArgumentException("Persona no encontrada"));

        DocumentDefinition def = documentDefinitionService.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado: " + documentId));

        PersonDocument pd = new PersonDocument();
        pd.setPerson(person);
        pd.setDocumentDefinition(def);
        pd.setStatus(status != null ? status : PersonDocumentStatus.VIGENTE);
        pd.setIssueDate(issueDate);
        pd.setExpiryDate(expiryDate);

        pd.setIssuerEntity(issuer);
        pd.setReviewStatus(ReviewStatus.PENDING);
        pd.setSubmittedByEntityUserId(null);

        PersonDocument savedPd = personDocumentRepository.save(pd);

        FileStorageService.StoredFileInfo info = fileStorageService.storePersonFile(person, file);

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

        issuingEntityService.ensureIssuerHasDocument(issuer, def.getId());

        return savedPd;
    }

    /**
     * Flujo administrativo: carga un archivo para una persona y crea el documento en estado de revisión.
     *
     * @param personId id de la persona
     * @param documentId id del documento del catálogo
     * @param status estado funcional del documento
     * @param issueDate fecha de emisión
     * @param expiryDate fecha de vencimiento
     * @param file archivo cargado
     * @return documento creado con su archivo asociado
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

        PersonDocument pd = new PersonDocument();
        pd.setPerson(person);
        pd.setDocumentDefinition(def);

        pd.setStatus(status != null ? status : PersonDocumentStatus.VIGENTE);
        pd.setIssueDate(issueDate);
        pd.setExpiryDate(expiryDate);

        pd.setIssuerEntity(issuer);
        pd.setReviewStatus(ReviewStatus.PENDING);

        PersonDocument savedPd = personDocumentRepository.save(pd);

        FileStorageService.StoredFileInfo info = fileStorageService.storePersonFile(person, file);

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

        if (issuer != null) {
            issuingEntityService.ensureIssuerHasDocument(issuer, def.getId());
        }

        return savedPd;
    }

    /**
     * Registra el resultado de revisión administrativa de un documento.
     *
     * @param personDocumentId id del documento a revisar
     * @param status nuevo estado de revisión
     * @param notes observaciones del revisor
     */
    @Transactional
    public void review(Long personDocumentId, ReviewStatus status, String notes) {

        PersonDocument pd = personDocumentRepository.findById(personDocumentId)
                .orElseThrow(() -> new IllegalArgumentException("PersonDocument no encontrado"));

        pd.setReviewStatus(status != null ? status : ReviewStatus.PENDING);
        pd.setReviewNotes((notes != null && !notes.isBlank()) ? notes.trim() : null);
        pd.setReviewedAt(LocalDateTime.now());
        pd.setReviewedByUserId(null);

        personDocumentRepository.save(pd);
    }
}
