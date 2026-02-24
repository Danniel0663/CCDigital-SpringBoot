package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.entity.AppUser;
import co.edu.unbosque.ccdigital.entity.Person;
import co.edu.unbosque.ccdigital.repository.AppUserRepository;
import co.edu.unbosque.ccdigital.repository.PersonRepository;
import co.edu.unbosque.ccdigital.security.IndyUserPrincipal;
import co.edu.unbosque.ccdigital.service.UserTotpService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoints REST para gestión de MFA TOTP del usuario final.
 *
 * <p>Permite iniciar el enrolamiento (generación de secreto), confirmar con un código de la app
 * autenticadora y desactivar TOTP. El secreto queda temporalmente en sesión hasta confirmación.</p>
 */
@RestController
@RequestMapping("/user/mfa/totp")
public class UserTotpController {

    private static final String SESSION_PENDING_TOTP_SECRETS = "user.mfa.totp.pendingSecretsByPersonId";
    private static final DateTimeFormatter UI_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final UserTotpService userTotpService;
    private final AppUserRepository appUserRepository;
    private final PersonRepository personRepository;

    public UserTotpController(UserTotpService userTotpService,
                              AppUserRepository appUserRepository,
                              PersonRepository personRepository) {
        this.userTotpService = userTotpService;
        this.appUserRepository = appUserRepository;
        this.personRepository = personRepository;
    }

    /**
     * Request para confirmar activación TOTP con código generado en la app.
     */
    public static class TotpConfirmRequest {
        private String code;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }
    }

    /**
     * Retorna el estado actual de TOTP del usuario autenticado.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(Authentication authentication) {
        AppUser user = currentUser(authentication);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", userTotpService.isTotpEnabled(user));
        out.put("confirmedAt", user.getTotpConfirmedAt() == null ? null : UI_DT.format(user.getTotpConfirmedAt()));
        return noStore(ResponseEntity.ok(out));
    }

    /**
     * Inicia configuración TOTP generando un secreto temporal y la URI {@code otpauth://}.
     */
    @PostMapping("/setup")
    public ResponseEntity<Map<String, Object>> setup(Authentication authentication,
                                                     HttpServletRequest request) {
        AppUser user = currentUser(authentication);
        String secret = userTotpService.generateSecretBase32();
        savePendingSecret(request, user.getPersonId(), secret);

        String label = normalize(user.getEmail());
        if (label.isBlank()) {
            label = "CC-" + user.getPersonId();
        }

        return noStore(ResponseEntity.ok(Map.of(
                "ok", true,
                "appName", "Aegis / Google Authenticator",
                "secret", secret,
                "otpauthUri", userTotpService.buildOtpAuthUri(label, secret)
        )));
    }

    /**
     * Confirma y activa TOTP validando un código de la app contra el secreto temporal.
     */
    @PostMapping("/confirm")
    public ResponseEntity<Map<String, Object>> confirm(@RequestBody TotpConfirmRequest req,
                                                       Authentication authentication,
                                                       HttpServletRequest request) {
        AppUser user = currentUser(authentication);
        String code = normalize(req == null ? null : req.getCode());
        if (code.isBlank()) {
            return noStore(ResponseEntity.badRequest().body(Map.of("error", "code es requerido")));
        }

        String secret = getPendingSecret(request, user.getPersonId());
        if (secret == null || secret.isBlank()) {
            return noStore(ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "La sesión de configuración TOTP expiró. Genera un nuevo código.")));
        }

        UserTotpService.VerificationResult vr = userTotpService.verifyCode(secret, code, null);
        if (!vr.valid()) {
            return noStore(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Código de la app inválido o expirado.")));
        }

        AppUser saved = userTotpService.enableTotp(user, secret, vr.acceptedTimeStep());
        removePendingSecret(request, user.getPersonId());

        return noStore(ResponseEntity.ok(Map.of(
                "ok", true,
                "enabled", true,
                "confirmedAt", saved.getTotpConfirmedAt() == null ? null : UI_DT.format(saved.getTotpConfirmedAt())
        )));
    }

    /**
     * Desactiva TOTP para el usuario autenticado.
     */
    @PostMapping("/disable")
    public ResponseEntity<Map<String, Object>> disable(Authentication authentication,
                                                       HttpServletRequest request) {
        AppUser user = currentUser(authentication);
        userTotpService.disableTotp(user);
        removePendingSecret(request, user.getPersonId());
        return noStore(ResponseEntity.ok(Map.of("ok", true, "enabled", false)));
    }

    private AppUser currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof IndyUserPrincipal p)) {
            throw new IllegalStateException("No hay usuario autenticado");
        }

        String idNumber = normalize(p.getIdNumber());
        Person person = personRepository.findByIdNumber(idNumber)
                .orElseThrow(() -> new IllegalStateException("No se encontró la persona autenticada"));

        return appUserRepository.findById(person.getId())
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .orElseThrow(() -> new IllegalStateException("No se encontró el usuario asociado"));
    }

    @SuppressWarnings("unchecked")
    private Map<Long, String> pendingSecrets(HttpServletRequest request, boolean createIfMissing) {
        var session = request.getSession(createIfMissing);
        if (session == null) return null;

        Object raw = session.getAttribute(SESSION_PENDING_TOTP_SECRETS);
        if (raw instanceof Map<?, ?> map) {
            return (Map<Long, String>) map;
        }

        if (!createIfMissing) return null;
        Map<Long, String> created = new LinkedHashMap<>();
        session.setAttribute(SESSION_PENDING_TOTP_SECRETS, created);
        return created;
    }

    private void savePendingSecret(HttpServletRequest request, Long personId, String secret) {
        if (personId == null) return;
        Map<Long, String> map = pendingSecrets(request, true);
        if (map != null) map.put(personId, secret);
    }

    private String getPendingSecret(HttpServletRequest request, Long personId) {
        if (personId == null) return null;
        Map<Long, String> map = pendingSecrets(request, false);
        return map == null ? null : map.get(personId);
    }

    private void removePendingSecret(HttpServletRequest request, Long personId) {
        if (personId == null) return;
        Map<Long, String> map = pendingSecrets(request, false);
        if (map != null) map.remove(personId);
    }

    private static ResponseEntity<Map<String, Object>> noStore(ResponseEntity<Map<String, Object>> response) {
        return ResponseEntity.status(response.getStatusCode())
                .cacheControl(CacheControl.noStore())
                .body(response.getBody());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
