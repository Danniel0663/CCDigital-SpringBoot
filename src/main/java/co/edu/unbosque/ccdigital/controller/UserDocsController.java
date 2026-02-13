package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.security.IndyUserPrincipal;
import co.edu.unbosque.ccdigital.service.FabricLedgerCliService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.http.HttpStatus.*;

/**
 * Controlador web para visualización/descarga de documentos del usuario final.
 *
 * <p>
 * Valida que el documento exista en Fabric y que la ruta física del archivo esté dentro del
 * directorio permitido configurado por {@code app.user-files-base-dir}.
 * </p>
 *
 * @since 3.0
 */
@Controller
public class UserDocsController {

    private final FabricLedgerCliService fabric;

    @Value("${app.user-files-base-dir:/home/ccdigital/CCDigitalBlock/CCDigital}")
    private String baseDir;

    /**
     * Constructor del controlador.
     *
     * @param fabric servicio de consulta de documentos en Fabric
     */
    public UserDocsController(FabricLedgerCliService fabric) {
        this.fabric = fabric;
    }

    /**
     * Visualiza un documento del usuario, sirviendo el archivo como recurso {@code inline}.
     *
     * @param docId identificador del documento en Fabric
     * @param auth autenticación actual
     * @return respuesta HTTP con el archivo como {@link Resource}
     */
    @GetMapping("/user/docs/view/{docId}")
    public ResponseEntity<Resource> viewDoc(@PathVariable String docId, Authentication auth) {
        IndyUserPrincipal p = (IndyUserPrincipal) auth.getPrincipal();

        var doc = fabric.findDocById(p.getIdType(), p.getIdNumber(), docId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Documento no encontrado en Fabric"));

        if (doc.filePath() == null || doc.filePath().isBlank()) {
            throw new ResponseStatusException(NOT_FOUND, "El documento no tiene ruta de archivo asociada");
        }

        try {
            Path base = Path.of(baseDir).toAbsolutePath().normalize();
            Path file = Path.of(doc.filePath()).toAbsolutePath().normalize();

            if (!file.startsWith(base)) {
                throw new ResponseStatusException(FORBIDDEN, "Ruta no permitida");
            }

            if (!Files.exists(file) || !Files.isReadable(file)) {
                throw new ResponseStatusException(NOT_FOUND, "Archivo no existe o no es legible");
            }

            String contentType = Files.probeContentType(file);
            if (contentType == null || contentType.isBlank()) {
                contentType = "application/octet-stream";
            }

            Resource resource = new FileSystemResource(file);

            ContentDisposition disposition = ContentDisposition
                    .inline()
                    .filename(doc.fileName())
                    .build();

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .body(resource);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "No se pudo abrir el documento", e);
        }
    }
}
