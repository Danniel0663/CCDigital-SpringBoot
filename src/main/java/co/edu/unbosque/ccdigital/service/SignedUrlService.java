package co.edu.unbosque.ccdigital.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

import static org.springframework.http.HttpStatus.FORBIDDEN;

/**
 * Genera y valida URLs firmadas de corta duración para vistas de documentos.
 *
 * <p>Objetivo: reducir el riesgo de reutilización/compartición de URLs directas con IDs o docIds.
 * La firma incluye identificadores del recurso + timestamp de expiración.</p>
 */
@Service
public class SignedUrlService {

    private static final Logger log = LoggerFactory.getLogger(SignedUrlService.class);
    private static final String HMAC_ALG = "HmacSHA256";
    private static final String SCOPE_USER_DOC = "user-doc-view";
    private static final String SCOPE_USER_DOC_DOWNLOAD = "user-doc-download";
    private static final String SCOPE_ISSUER_DOC = "issuer-doc-view";
    private static final String SCOPE_ISSUER_DOC_DOWNLOAD = "issuer-doc-download";

    @Value("${app.security.signed-urls.secret:}")
    private String configuredSecret;

    @Value("${app.security.signed-urls.ttl-seconds:300}")
    private long ttlSeconds;

    private volatile byte[] secretBytes;

    /**
     * Construye una URL firmada para visualización de documento del usuario final.
     *
     * @param docId identificador del documento en Fabric
     * @return URL relativa firmada con expiración
     */
    public String userDocumentViewUrl(String docId) {
        long exp = expiryEpochSeconds();
        String normalizedDocId = safe(docId);
        String sig = sign(SCOPE_USER_DOC, normalizedDocId, Long.toString(exp));
        String encodedDocId = UriUtils.encodePathSegment(
                Objects.requireNonNull(normalizedDocId),
                Objects.requireNonNull(StandardCharsets.UTF_8)
        );
        return "/user/docs/view/" + encodedDocId + "?exp=" + exp + "&sig=" + sig;
    }

    /**
     * Construye una URL firmada para descarga de documento del usuario final.
     *
     * @param docId identificador del documento en Fabric
     * @return URL relativa firmada con expiración para descarga
     */
    public String userDocumentDownloadUrl(String docId) {
        long exp = expiryEpochSeconds();
        String normalizedDocId = safe(docId);
        String sig = sign(SCOPE_USER_DOC_DOWNLOAD, normalizedDocId, Long.toString(exp));
        String encodedDocId = UriUtils.encodePathSegment(
                Objects.requireNonNull(normalizedDocId),
                Objects.requireNonNull(StandardCharsets.UTF_8)
        );
        return "/user/docs/download/" + encodedDocId + "?exp=" + exp + "&sig=" + sig;
    }

    /**
     * Construye una URL firmada para visualización de documento aprobado del módulo emisor.
     *
     * @param requestId id de la solicitud de acceso
     * @param personDocumentId id del documento de persona solicitado
     * @return URL relativa firmada con expiración
     */
    public String issuerDocumentViewUrl(Long requestId, Long personDocumentId) {
        long exp = expiryEpochSeconds();
        String rid = requestId == null ? "" : requestId.toString();
        String pdid = personDocumentId == null ? "" : personDocumentId.toString();
        String sig = sign(SCOPE_ISSUER_DOC, rid, pdid, Long.toString(exp));
        return "/issuer/access-requests/" + rid + "/documents/" + pdid + "/view?exp=" + exp + "&sig=" + sig;
    }

    /**
     * Construye una URL firmada para descarga de documento aprobado del módulo emisor.
     *
     * @param requestId id de la solicitud de acceso
     * @param personDocumentId id del documento de persona solicitado
     * @return URL relativa firmada con expiración para descarga
     */
    public String issuerDocumentDownloadUrl(Long requestId, Long personDocumentId) {
        long exp = expiryEpochSeconds();
        String rid = requestId == null ? "" : requestId.toString();
        String pdid = personDocumentId == null ? "" : personDocumentId.toString();
        String sig = sign(SCOPE_ISSUER_DOC_DOWNLOAD, rid, pdid, Long.toString(exp));
        return "/issuer/access-requests/" + rid + "/documents/" + pdid + "/download?exp=" + exp + "&sig=" + sig;
    }

    /**
     * Valida firma y expiración de una URL de documento del usuario final.
     *
     * @param docId docId de Fabric en la URL
     * @param exp expiración UNIX epoch seconds
     * @param sig firma recibida en query string
     */
    public void validateUserDocumentView(String docId, Long exp, String sig) {
        validate(SCOPE_USER_DOC, exp, sig, safe(docId));
    }

    /**
     * Valida firma y expiración de una URL de descarga de documento del usuario final.
     *
     * @param docId docId de Fabric en la URL
     * @param exp expiración UNIX epoch seconds
     * @param sig firma recibida en query string
     */
    public void validateUserDocumentDownload(String docId, Long exp, String sig) {
        validate(SCOPE_USER_DOC_DOWNLOAD, exp, sig, safe(docId));
    }

    /**
     * Valida firma y expiración de una URL de documento del módulo emisor.
     *
     * @param requestId solicitud de acceso
     * @param personDocumentId documento solicitado
     * @param exp expiración UNIX epoch seconds
     * @param sig firma recibida en query string
     */
    public void validateIssuerDocumentView(Long requestId, Long personDocumentId, Long exp, String sig) {
        validate(
                SCOPE_ISSUER_DOC,
                exp,
                sig,
                requestId == null ? "" : requestId.toString(),
                personDocumentId == null ? "" : personDocumentId.toString()
        );
    }

    /**
     * Valida firma y expiración de una URL de descarga del módulo emisor.
     *
     * @param requestId solicitud de acceso
     * @param personDocumentId documento solicitado
     * @param exp expiración UNIX epoch seconds
     * @param sig firma recibida en query string
     */
    public void validateIssuerDocumentDownload(Long requestId, Long personDocumentId, Long exp, String sig) {
        validate(
                SCOPE_ISSUER_DOC_DOWNLOAD,
                exp,
                sig,
                requestId == null ? "" : requestId.toString(),
                personDocumentId == null ? "" : personDocumentId.toString()
        );
    }

    private void validate(String scope, Long exp, String sig, String... values) {
        // Se valida primero expiración y luego firma HMAC para rechazar URLs reutilizadas/manipuladas.
        if (exp == null || sig == null || sig.isBlank()) {
            throw new ResponseStatusException(FORBIDDEN, "URL de acceso inválida");
        }
        long now = Instant.now().getEpochSecond();
        if (exp < now) {
            throw new ResponseStatusException(FORBIDDEN, "La URL de acceso expiró");
        }
        String expected = sign(scope, concat(values, Long.toString(exp)));
        if (!constantTimeEquals(expected, sig)) {
            throw new ResponseStatusException(FORBIDDEN, "Firma de URL inválida");
        }
    }

    private long expiryEpochSeconds() {
        long ttl = Math.max(30, ttlSeconds);
        return Instant.now().plusSeconds(ttl).getEpochSecond();
    }

    /**
     * Firma un payload de URL usando HMAC-SHA256 y salida Base64 URL-safe.
     *
     * @param scope ámbito lógico de la URL (usuario/emisor)
     * @param values valores del recurso a proteger
     * @return firma compacta apta para query string
     */
    private String sign(String scope, String... values) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(resolveSecretBytes(), HMAC_ALG));
            String payload = scope + "|" + String.join("|", values);
            byte[] h = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(h);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar URL", e);
        }
    }

    private byte[] resolveSecretBytes() {
        byte[] local = secretBytes;
        if (local != null) return local;
        synchronized (this) {
            if (secretBytes != null) return secretBytes;
            String raw = configuredSecret == null ? "" : configuredSecret.trim();
            if (raw.isBlank()) {
                byte[] random = new byte[32];
                new SecureRandom().nextBytes(random);
                secretBytes = random;
                // En desarrollo permite arrancar sin configuración explícita; invalida firmas tras reinicio.
                log.warn("SignedUrlService: app.security.signed-urls.secret no configurado; se usará uno temporal para esta ejecución.");
                return secretBytes;
            }
            secretBytes = raw.getBytes(StandardCharsets.UTF_8);
            return secretBytes;
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        byte[] ab = a == null ? new byte[0] : a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b == null ? new byte[0] : b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(ab, bb);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String[] concat(String[] values, String last) {
        String[] out = new String[values.length + 1];
        System.arraycopy(values, 0, out, 0, values.length);
        out[values.length] = last;
        return out;
    }
}
