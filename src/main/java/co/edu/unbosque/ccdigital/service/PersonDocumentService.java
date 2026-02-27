package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.dto.PersonDocumentRequest;
import co.edu.unbosque.ccdigital.entity.*;
import co.edu.unbosque.ccdigital.repository.FileRecordRepository;
import co.edu.unbosque.ccdigital.repository.PersonDocumentRepository;
import co.edu.unbosque.ccdigital.repository.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Servicio de negocio para la gestión de documentos asociados a personas ({@link PersonDocument}).
 *
 * <p>Este servicio centraliza la lógica para:</p>
 * <ul>
 *   <li>Listar y consultar documentos por persona (con relaciones cargadas).</li>
 *   <li>Crear registros {@link PersonDocument} desde API (con o sin archivo ya referenciado por path).</li>
 *   <li>Radicación de documentos desde emisor (issuer) con workflow de revisión.</li>
 *   <li>Carga administrativa de documentos con almacenamiento físico y metadatos.</li>
 *   <li>Registrar revisión (aprobación/rechazo/pending) y auditoría mínima.</li>
 * </ul>
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
     * API: crea un {@link PersonDocument} a partir de un {@link PersonDocumentRequest}.
     *
     * <p>Comportamiento:</p>
     * <ul>
     *   <li>Valida que exista la persona y el tipo de documento del catálogo.</li>
     *   <li>Resuelve el emisor por nombre (si el catálogo define {@code issuingEntity}).</li>
     *   <li>Inicializa estado del documento y el workflow de revisión ({@link ReviewStatus#PENDING}).</li>
     *   <li>Si el request trae {@code storagePath}, crea un {@link FileRecord} básico (storedAs PATH).</li>
     *   <li>Asegura relación emisor-documento en la tabla puente {@code entity_document_definitions}.</li>
     * </ul>
     *
     * @param request request con la información del documento
     * @return {@link PersonDocument} persistido
     * @throws IllegalArgumentException si no existe persona o definición de documento
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
     * ISSUER: el emisor carga un documento para una persona y queda en revisión ({@link ReviewStatus#PENDING}).
     *
     * <p>Flujo:</p>
     * <ol>
     *   <li>Valida parámetros obligatorios.</li>
     *   <li>Valida que el documento esté permitido para el emisor.</li>
     *   <li>Crea el {@link PersonDocument} con {@link ReviewStatus#PENDING}.</li>
     *   <li>Almacena el archivo en disco con {@link FileStorageService} (hash + size + path relativo).</li>
     *   <li>Crea el {@link FileRecord} asociado (storedAs PATH).</li>
     *   <li>Asegura la relación emisor-documento en la tabla puente.</li>
     * </ol>
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
        // Regla de seguridad del módulo emisor: solo se permite PDF.
        if (!isPdfFile(file)) throw new IllegalArgumentException("Solo se permite subir archivos PDF.");

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

        // Al validar y almacenar solo PDF, se fija MIME canónico.
        String mime = "application/pdf";

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
     * Valida que el archivo recibido sea un PDF real.
     *
     * <p>Se verifica por:
     * <ul>
     *   <li>Extensión/mime declarados por el cliente</li>
     *   <li>Firma binaria inicial {@code %PDF}</li>
     * </ul>
     * De esta forma no se depende solo de la extensión del nombre.</p>
     *
     * @param file archivo multipart recibido
     * @return {@code true} si el archivo cumple validación PDF
     */
    private boolean isPdfFile(MultipartFile file) {
        if (file == null || file.isEmpty()) return false;

        String originalName = file.getOriginalFilename();
        String normalizedName = originalName == null ? "" : originalName.trim().toLowerCase(Locale.ROOT);
        boolean hasPdfExtension = normalizedName.endsWith(".pdf");

        String contentType = file.getContentType();
        String normalizedMime = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        boolean hasPdfMime = normalizedMime.contains("pdf");

        boolean hasPdfSignature;
        try (var in = file.getInputStream()) {
            byte[] header = in.readNBytes(4);
            hasPdfSignature = header.length == 4
                    && header[0] == '%'
                    && header[1] == 'P'
                    && header[2] == 'D'
                    && header[3] == 'F';
        } catch (IOException ex) {
            return false;
        }

        return hasPdfSignature && (hasPdfExtension || hasPdfMime);
    }

    /**
     * ADMIN: carga un documento para una persona desde el módulo administrativo.
     *
     * <p>Flujo:</p>
     * <ol>
     *   <li>Valida que existan persona y tipo de documento.</li>
     *   <li>Resuelve emisor por nombre desde el catálogo.</li>
     *   <li>Crea {@link PersonDocument} con {@link ReviewStatus#PENDING}.</li>
     *   <li>Almacena archivo en disco (hash + size + path relativo).</li>
     *   <li>Crea {@link FileRecord} asociado (storedAs PATH).</li>
     *   <li>Asegura relación emisor-documento en tabla puente.</li>
     * </ol>
     *
     * @param personId id de la persona
     * @param documentId id del tipo de documento (catálogo)
     * @param status estado funcional del documento (si es null, usa {@link PersonDocumentStatus#VIGENTE})
     * @param issueDate fecha de expedición
     * @param expiryDate fecha de vencimiento
     * @param file archivo cargado (multipart)
     * @return {@link PersonDocument} persistido con su archivo asociado
     * @throws IllegalArgumentException si no existe persona o documento
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
        pd.setReviewedByUserId(null);

        personDocumentRepository.save(pd);
    }
}
