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
 * Controlador REST para la gestión de documentos asociados a personas y descarga de archivos.
 *
 * <p>Expone endpoints bajo el prefijo {@code /api}</p>
 *
 * <p><b>Descarga de archivos:</b> el endpoint de descarga valida que el {@code fileId}
 * pertenezca al {@code PersonDocument} indicado por {@code personDocId} antes de cargar el
 * recurso con {@link FileStorageService}.</p>
 *
 * @author Danniel
 * @author Yeison
 * @since 1.0
 */
@RestController
@RequestMapping("/api")
public class PersonDocumentRestController {

    /**
     * Servicio de negocio para operaciones sobre documentos asociados a personas.
     */
    private final PersonDocumentService personDocumentService;

    /**
     * Servicio de almacenamiento/carga de archivos desde el sistema (o repositorio configurado).
     */
    private final FileStorageService fileStorageService;

    /**
     * Construye el controlador REST inyectando dependencias.
     *
     * @param personDocumentService servicio para documentos de persona
     * @param fileStorageService servicio para cargar archivos como {@link Resource}
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
     * @return lista de {@link PersonDocument} asociados a la persona
     */
    @GetMapping("/persons/{personId}/documents")
    public List<PersonDocument> listByPerson(@PathVariable Long personId) {
        return personDocumentService.listByPerson(personId);
    }

    /**
     * Crea un documento asociado a una persona usando un request JSON.
     *
     * <p>Endpoint: {@code POST /api/persons/{personId}/documents}</p>
     *
     * <p>El {@code personId} se toma de la ruta y se fuerza en el {@link PersonDocumentRequest}
     * para asegurar consistencia.</p>
     *
     * @param personId identificador interno de la persona
     * @param request request con la información necesaria para crear el documento
     * @return {@link PersonDocument} creado
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
     * @param id identificador interno del {@link PersonDocument}
     * @return documento asociado a persona
     */
    @GetMapping("/person-documents/{id}")
    public PersonDocument getPersonDocument(@PathVariable Long id) {
        return personDocumentService.getById(id);
    }

    /**
     * Descarga o visualiza (inline) un archivo asociado a un documento de persona.
     * 
     * @param personDocId id del {@link PersonDocument} propietario del archivo
     * @param fileId id del archivo a descargar/visualizar
     * @return {@link ResponseEntity} con el {@link Resource} del archivo
     * @throws IllegalArgumentException si el archivo no pertenece al {@link PersonDocument} indicado
     */
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
