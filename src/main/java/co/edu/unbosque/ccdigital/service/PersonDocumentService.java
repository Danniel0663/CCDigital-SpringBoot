package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.dto.PersonDocumentRequest;
import co.edu.unbosque.ccdigital.entity.*;
import co.edu.unbosque.ccdigital.repository.FileRecordRepository;
import co.edu.unbosque.ccdigital.repository.PersonDocumentRepository;
import co.edu.unbosque.ccdigital.repository.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class PersonDocumentService {

    private final PersonRepository personRepository;
    private final DocumentDefinitionService documentDefinitionService;
    private final PersonDocumentRepository personDocumentRepository;
    private final FileRecordRepository fileRecordRepository;
    private final FileStorageService fileStorageService;

    public PersonDocumentService(PersonRepository personRepository,
                                 DocumentDefinitionService documentDefinitionService,
                                 PersonDocumentRepository personDocumentRepository,
                                 FileRecordRepository fileRecordRepository,
                                 FileStorageService fileStorageService) {
        this.personRepository = personRepository;
        this.documentDefinitionService = documentDefinitionService;
        this.personDocumentRepository = personDocumentRepository;
        this.fileRecordRepository = fileRecordRepository;
        this.fileStorageService = fileStorageService;
    }

    public List<PersonDocument> listByPerson(Long personId) {
        return personDocumentRepository.findByPersonIdWithFiles(personId);
    }

    public PersonDocument getById(Long id) {
        return personDocumentRepository.findByIdWithFiles(id)
                .orElseThrow(() -> new IllegalArgumentException("PersonDocument no encontrado"));
    }

    // API: crea PersonDocument y FileRecord si ya viene storagePath
    @Transactional
    public PersonDocument create(PersonDocumentRequest request) {
        Person person = personRepository.findById(request.getPersonId())
                .orElseThrow(() -> new IllegalArgumentException("Persona no encontrada"));

        DocumentDefinition def = documentDefinitionService.findById(request.getDocumentId())
                .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado: " + request.getDocumentId()));

        PersonDocument pd = new PersonDocument();
        pd.setPerson(person);
        pd.setDocumentDefinition(def);
        pd.setStatus(request.getStatus() != null ? request.getStatus() : PersonDocumentStatus.VIGENTE);
        pd.setIssueDate(request.getIssueDate());
        pd.setExpiryDate(request.getExpiryDate());

        PersonDocument saved = personDocumentRepository.save(pd);

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
        }

        return saved;
    }

    // ADMIN: subir archivo y calcular hash automáticamente
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

        // 1) Crear person_document
        PersonDocument pd = new PersonDocument();
        pd.setPerson(person);
        pd.setDocumentDefinition(def);
        pd.setStatus(status != null ? status : PersonDocumentStatus.VIGENTE);
        pd.setIssueDate(issueDate);
        pd.setExpiryDate(expiryDate);

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

        // para que al devolverlo ya venga el archivo en la lista (si OpenInView está off, igual el repo fetch ayuda)
        savedPd.addFile(fr);

        return savedPd;
    }
}
