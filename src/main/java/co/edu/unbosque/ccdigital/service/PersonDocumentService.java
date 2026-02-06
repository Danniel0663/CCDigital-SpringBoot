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

    public List<PersonDocument> listByPerson(Long personId) {
        return personDocumentRepository.findByPersonIdWithFiles(personId);
    }

    public PersonDocument getById(Long id) {
        return personDocumentRepository.findByIdWithFiles(id)
                .orElseThrow(() -> new IllegalArgumentException("PersonDocument no encontrado"));
    }

    /**
     * API: crea PersonDocument y opcionalmente un FileRecord si ya viene storagePath.
     * - Asigna issuerEntity según def.issuingEntity (tabla entities)
     * - Asigna reviewStatus = PENDING por defecto
     * - Genera relación issuer<->document en entity_document_definitions
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
     * ISSUER: el emisor carga un documento para una persona y queda en revisión (PENDING).
     * Valida que el documento esté permitido para el emisor.
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
     * ADMIN: sube archivo, calcula hash, crea FileRecord y deja el PersonDocument en PENDING.
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
     * Cambia el estado de revisión y guarda auditoría mínima.
     * Login pendiente => reviewedByUserId = null por ahora.
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
