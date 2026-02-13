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
 * Controlador REST para gestión de documentos asociados a personas y descarga de archivos.
 *
 * <p>
 * Expone endpoints bajo {@code /api} para consultar documentos y descargar archivos asociados.
 * </p>
 *
 * <p>
 * El endpoint de descarga valida que el archivo pertenezca al documento de persona indicado
 * antes de cargar el recurso desde {@link FileStorageService}.
 * </p>
 *
 * @since 1.0
 */
@RestController
@RequestMapping("/api")
public class PersonDocumentRestController {

    private final PersonDocumentService personDocumentService;
    private final FileStorageService fileStorageService;

    /**
     * Constructor del controlador.
     *
     * @param personDocumentService servicio para documentos de persona
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
     * @param personId identificador interno de la persona
     * @return lista de documentos asociados a la persona
     */
    @GetMapping("/persons/{personId}/documents")
    public List<PersonDocument> listByPerson(@PathVariable("personId") Long personId) {
        return personDocumentService.listByPerson(personId);
    }

    /**
     * Crea un documento asociado a una persona usando un request JSON.
     *
     * @param personId identificador interno de la persona
     * @param request request con la información del documento
     * @return documento creado
     */
    @PostMapping("/persons/{personId}/documents")
    public PersonDocument createForPerson(@PathVariable("personId") Long personId,
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
    public PersonDocument getPersonDocument(@PathVariable("id") Long id) {
        return personDocumentService.getById(id);
    }

    /**
     * Descarga o visualiza (inline) un archivo asociado a un documento de persona.
     *
     * @param personDocId id del documento de persona propietario del archivo
     * @param fileId id del archivo a descargar/visualizar
     * @return respuesta HTTP con el recurso del archivo
     * @throws IllegalArgumentException si el archivo no pertenece al documento indicado
     */
    @GetMapping("/person-documents/{personDocId}/files/{fileId}")
    public ResponseEntity<Resource> downloadFile(@PathVariable("personDocId") Long personDocId,
                                                 @PathVariable("fileId") Long fileId) {

        PersonDocument pd = personDocumentService.getById(personDocId);

        FileRecord fileRecord = pd.getFiles().stream()
                .filter(f -> f.getId().equals(fileId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Archivo no pertenece al documento indicado."));

        Resource resource = fileStorageService.loadAsResource(fileRecord);

        String contentType = (fileRecord.getMimeType() != null && !fileRecord.getMimeType().isBlank())
                ? fileRecord.getMimeType()
                : "application/octet-stream";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }
}
