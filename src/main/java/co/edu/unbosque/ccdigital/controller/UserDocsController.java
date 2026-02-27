package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.security.IndyUserPrincipal;
import co.edu.unbosque.ccdigital.service.FabricLedgerCliService;
import co.edu.unbosque.ccdigital.service.SignedUrlService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

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
    private final SignedUrlService signedUrlService;

    @Value("${app.user-files-base-dir:/home/ccdigital/CCDigitalBlock/CCDigital}")
    private String baseDir;

    /**
     * Constructor del controlador.
     *
     * @param fabric servicio de consulta de documentos en Fabric
     */
    public UserDocsController(FabricLedgerCliService fabric, SignedUrlService signedUrlService) {
        this.fabric = fabric;
        this.signedUrlService = signedUrlService;
    }

    /**
     * Visualiza un documento del usuario, sirviendo el archivo como recurso {@code inline}.
     *
     * @param docId identificador del documento en Fabric
     * @param exp expiración de la URL firmada (epoch seconds)
     * @param sig firma HMAC de la URL
     * @param auth autenticación actual
     * @return respuesta HTTP con el archivo como {@link Resource}
     */
    @GetMapping("/user/docs/view/{docId}")
    public ResponseEntity<Resource> viewDoc(@PathVariable String docId,
                                            @RequestParam("exp") Long exp,
                                            @RequestParam("sig") String sig,
                                            Authentication auth) {
        return serveDoc(docId, exp, sig, auth, false);
    }

    /**
     * Descarga un documento del usuario, sirviéndolo como archivo adjunto.
     *
     * @param docId identificador del documento en Fabric
     * @param exp expiración de la URL firmada (epoch seconds)
     * @param sig firma HMAC de la URL
     * @param auth autenticación actual
     * @return respuesta HTTP con el archivo como {@link Resource}
     */
    @GetMapping("/user/docs/download/{docId}")
    public ResponseEntity<Resource> downloadDoc(@PathVariable String docId,
                                                @RequestParam("exp") Long exp,
                                                @RequestParam("sig") String sig,
                                                Authentication auth) {
        return serveDoc(docId, exp, sig, auth, true);
    }

    /**
     * Resuelve un documento de Fabric y lo responde en modo inline o attachment.
     *
     * @param docId identificador del documento en Fabric
     * @param exp expiración de la URL firmada (epoch seconds)
     * @param sig firma HMAC de la URL
     * @param auth autenticación actual del usuario final
     * @param asAttachment {@code true} para descarga; {@code false} para vista integrada
     * @return respuesta HTTP con el archivo del documento
     */
    private ResponseEntity<Resource> serveDoc(String docId,
                                              Long exp,
                                              String sig,
                                              Authentication auth,
                                              boolean asAttachment) {
        IndyUserPrincipal p = (IndyUserPrincipal) auth.getPrincipal();
        if (asAttachment) {
            signedUrlService.validateUserDocumentDownload(docId, exp, sig);
        } else {
            signedUrlService.validateUserDocumentView(docId, exp, sig);
        }

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

            // Intenta detectar MIME real para soportar vista inline (PDF/imagen) en el modal.
            String contentType = detectContentType(file, doc.fileName());

            Resource resource = new FileSystemResource(file);

            ContentDisposition.Builder dispositionBuilder = asAttachment
                    ? ContentDisposition.attachment()
                    : ContentDisposition.inline();
            ContentDisposition disposition = dispositionBuilder
                    .filename(doc.fileName())
                    .build();
            String safeContentType = (contentType == null || contentType.isBlank())
                    ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                    : contentType;
            MediaType responseContentType = MediaType.parseMediaType(Objects.requireNonNull(safeContentType));

            return ResponseEntity.ok()
                    .contentType(responseContentType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .body(resource);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "No se pudo abrir el documento", e);
        }
    }

    /**
     * Detecta el tipo de contenido del archivo para favorecer renderizado inline en navegador.
     *
     * <p>Primero usa el filesystem, luego extensión de nombre lógico y por último extensión
     * del path físico. Si no se puede resolver, retorna {@code application/octet-stream}.</p>
     *
     * @param file ruta física
     * @param logicalName nombre lógico del documento
     * @return MIME type seguro para responder
     */
    private String detectContentType(Path file, String logicalName) {
        try {
            String byFs = Files.probeContentType(file);
            if (byFs != null && !byFs.isBlank()) {
                return byFs;
            }
        } catch (Exception ignored) {
            // Continúa con detección por extensión.
        }

        if (logicalName != null && !logicalName.isBlank()) {
            var mt = MediaTypeFactory.getMediaType(logicalName);
            if (mt.isPresent()) {
                return mt.get().toString();
            }
        }

        String physicalName = file.getFileName() != null ? file.getFileName().toString() : "";
        if (!physicalName.isBlank()) {
            var mt = MediaTypeFactory.getMediaType(physicalName);
            if (mt.isPresent()) {
                return mt.get().toString();
            }
        }

        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}
