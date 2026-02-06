package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.dto.PersonDocumentRequest;
import co.edu.unbosque.ccdigital.entity.FileRecord;
import co.edu.unbosque.ccdigital.entity.PersonDocument;
import co.edu.unbosque.ccdigital.service.FileStorageService;
import co.edu.unbosque.ccdigital.service.PersonDocumentService;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class PersonDocumentRestController {

    private final PersonDocumentService personDocumentService;
    private final FileStorageService fileStorageService;

    public PersonDocumentRestController(PersonDocumentService personDocumentService,
                                        FileStorageService fileStorageService) {
        this.personDocumentService = personDocumentService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/persons/{personId}/documents")
    public List<PersonDocument> listByPerson(@PathVariable Long personId) {
        return personDocumentService.listByPerson(personId);
    }

    @PostMapping("/persons/{personId}/documents")
    public PersonDocument createForPerson(@PathVariable Long personId,
                                          @RequestBody PersonDocumentRequest request) {
        request.setPersonId(personId);
        return personDocumentService.create(request);
    }

    @GetMapping("/person-documents/{id}")
    public PersonDocument getPersonDocument(@PathVariable Long id) {
        return personDocumentService.getById(id);
    }

    @GetMapping("/person-documents/{personDocId}/files/{fileId}")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long personDocId,
                                                 @PathVariable Long fileId) {

        PersonDocument pd = personDocumentService.getById(personDocId);

        FileRecord fileRecord = pd.getFiles().stream()
                .filter(f -> f.getId().equals(fileId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Archivo no pertenece a este PersonDocument"));

        Resource resource = fileStorageService.loadAsResource(fileRecord);

        String contentType = fileRecord.getMimeType() != null
                ? fileRecord.getMimeType()
                : "application/octet-stream";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }
}
