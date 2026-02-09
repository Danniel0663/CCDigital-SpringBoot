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

/**
 * API REST para la gestión de documentos de una persona y descarga de archivos asociados.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api")
public class PersonDocumentRestController {

    private final PersonDocumentService personDocumentService;
    private final FileStorageService fileStorageService;

    /**
     * Constructor con inyección de dependencias.
     *
     * @param personDocumentService servicio de documentos por persona
     * @param fileStorageService servicio de almacenamiento de archivos
     */
    public PersonDocumentRestController(PersonDocumentService personDocumentService,
                                        FileStorageService fileStorageService) {
        this.personDocumentService = personDocumentService;
        this.fileStorageService = fileStorageService;
    }

    /**
     * Lista los documentos asociados a una persona.
     *
     * @param personId identificador de la persona
     * @return lista de documentos de la persona
     */
    @GetMapping("/persons/{personId}/documents")
    public List<PersonDocument> listByPerson(@PathVariable Long personId) {
        return personDocumentService.listByPerson(personId);
    }

    /**
     * Crea un registro de documento para una persona.
     *
     * @param personId identificador de la persona
     * @param request datos del documento
     * @return registro creado
     */
    @PostMapping("/persons/{personId}/documents")
    public PersonDocument createForPerson(@PathVariable Long personId,
                                          @RequestBody PersonDocumentRequest request) {
        request.setPersonId(personId);
        return personDocumentService.create(request);
    }

    /**
     * Obtiene un documento de persona por su identificador.
     *
     * @param id identificador del documento
     * @return documento encontrado
     */
    @GetMapping("/person-documents/{id}")
    public PersonDocument getPersonDocument(@PathVariable Long id) {
        return personDocumentService.getById(id);
    }

    /**
     * Descarga (o presenta en línea) un archivo asociado a un documento de persona.
     *
     * <p>El archivo se valida contra el documento para asegurar pertenencia.</p>
     *
     * @param personDocId identificador del documento de persona
     * @param fileId identificador del archivo
     * @return recurso descargable con cabeceras HTTP apropiadas
     */
    @GetMapping("/person-documents/{personDocId}/files/{fileId}")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long personDocId,
                                                 @PathVariable Long fileId) {

        PersonDocument pd = personDocumentService.getById(personDocId);

        FileRecord fileRecord = pd.getFiles().stream()
                .filter(f -> f.getId().equals(fileId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("El archivo no pertenece al documento indicado."));

        Resource resource = fileStorageService.loadAsResource(fileRecord);

        String contentType = (fileRecord.getMimeType() != null && !fileRecord.getMimeType().isBlank())
                ? fileRecord.getMimeType()
                : "application/octet-stream";

        String downloadName = (fileRecord.getOriginalName() != null && !fileRecord.getOriginalName().isBlank())
                ? fileRecord.getOriginalName()
                : resource.getFilename();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + downloadName + "\"")
                .body(resource);
    }
}
