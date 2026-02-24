package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.dto.UserRegisterForm;
import co.edu.unbosque.ccdigital.entity.AppUser;
import co.edu.unbosque.ccdigital.entity.IdType;
import co.edu.unbosque.ccdigital.repository.AppUserRepository;
import co.edu.unbosque.ccdigital.service.UserAccountService;
import co.edu.unbosque.ccdigital.service.UserTotpService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controlador web para registro de cuentas de usuario final.
 *
 * <p>
 * Expone la vista y procesamiento de {@code /register/user}, donde se capturan
 * datos completos del ciudadano y se delega la creación de la cuenta al servicio
 * de negocio.
 * </p>
 *
 * <p>
 * Además, soporta un flujo opcional de activación TOTP inmediatamente después del registro:
 * genera un secreto temporal, muestra la configuración (clave/QR) en la misma vista y confirma
 * la activación mediante un endpoint AJAX antes de que el usuario inicie sesión por primera vez.
 * </p>
 *
 * @since 3.0
 */
@Controller
@RequestMapping("/register/user")
public class UserRegistrationController {

    private static final String SESSION_REGISTER_PENDING_TOTP_SECRETS = "register.user.pendingTotpSecretsByPersonId";

    private final UserAccountService userAccountService;
    private final UserTotpService userTotpService;
    private final AppUserRepository appUserRepository;

    /**
     * Constructor del controlador.
     *
     * @param userAccountService servicio de registro de usuarios
     * @param userTotpService servicio de generación/validación TOTP para activación opcional
     * @param appUserRepository repositorio de usuarios para confirmar activación post-registro
     */
    public UserRegistrationController(UserAccountService userAccountService,
                                      UserTotpService userTotpService,
                                      AppUserRepository appUserRepository) {
        this.userAccountService = userAccountService;
        this.userTotpService = userTotpService;
        this.appUserRepository = appUserRepository;
    }

    /**
     * Request AJAX para confirmar TOTP luego del registro.
     */
    public static class RegisterTotpConfirmRequest {
        private Long personId;
        private String code;

        /**
         * @return {@code person_id} del usuario recién creado
         */
        public Long getPersonId() {
            return personId;
        }

        /**
         * @param personId identificador del usuario recién creado
         */
        public void setPersonId(Long personId) {
            this.personId = personId;
        }

        /**
         * @return código TOTP generado en la app autenticadora
         */
        public String getCode() {
            return code;
        }

        /**
         * @param code código TOTP generado en la app autenticadora
         */
        public void setCode(String code) {
            this.code = code;
        }
    }

    /**
     * Muestra el formulario de registro de usuario final.
     *
     * <p>
     * Además de inicializar el DTO del formulario, carga el catálogo de tipos
     * de identificación para poblar el selector de la vista.
     * </p>
     *
     * @param model modelo de Spring MVC
     * @return vista del formulario de registro
     */
    @GetMapping
    public String form(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new UserRegisterForm());
        }
        model.addAttribute("idTypes", IdType.values());
        return "auth/register-user";
    }

    /**
     * Crea un usuario final a partir de una persona existente.
     *
     * <p>
     * En caso de error de validación, conserva los datos no sensibles del formulario
     * y limpia los campos de contraseña antes de devolver la misma vista.
     * </p>
     *
     * @param form datos del formulario
     * @param model modelo de Spring MVC
     * @param request request HTTP para almacenar temporalmente secreto TOTP si aplica
     * @return vista del formulario con resultado de operación
     */
    @PostMapping
    public String register(@ModelAttribute("form") UserRegisterForm form,
                           Model model,
                           HttpServletRequest request) {
        model.addAttribute("idTypes", IdType.values());
        try {
            AppUser createdUser = userAccountService.registerFromExistingPerson(form);

            model.addAttribute("success", "Usuario creado correctamente.");
            model.addAttribute("createdEmail", createdUser.getEmail());
            prepareOptionalTotpSetupIfRequested(form, createdUser, request, model);
            model.addAttribute("form", new UserRegisterForm());
        } catch (IllegalArgumentException ex) {
            form.setPassword(null);
            form.setConfirmPassword(null);
            model.addAttribute("form", form);
            model.addAttribute("error", ex.getMessage());
        }

        return "auth/register-user";
    }

    /**
     * Confirma la activación de TOTP de forma opcional después del registro.
     *
     * <p>Este endpoint no requiere sesión autenticada, pero exige que el secreto TOTP esté pendiente
     * en la sesión de registro y que el {@code personId} corresponda al usuario recién creado.</p>
     *
     * @param req request con {@code personId} y código TOTP
     * @param request request HTTP con la sesión del flujo de registro
     * @return respuesta JSON con estado de activación
     */
    @PostMapping("/totp/confirm")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> confirmRegisterTotp(@RequestBody RegisterTotpConfirmRequest req,
                                                                   HttpServletRequest request) {
        Long personId = req == null ? null : req.getPersonId();
        String code = normalize(req == null ? null : req.getCode());

        if (personId == null) {
            return noStore(ResponseEntity.badRequest().body(Map.of("error", "personId es requerido")));
        }
        if (code.isBlank()) {
            return noStore(ResponseEntity.badRequest().body(Map.of("error", "code es requerido")));
        }

        String secret = getPendingTotpSecret(request, personId);
        if (secret == null || secret.isBlank()) {
            return noStore(ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "La configuración del autenticador expiró. Regístrate o configúralo desde tu dashboard.")));
        }

        AppUser user = appUserRepository.findById(personId)
                .orElse(null);
        if (user == null) {
            return noStore(ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No se encontró el usuario recién registrado.")));
        }

        UserTotpService.VerificationResult vr = userTotpService.verifyCode(secret, code, null);
        if (!vr.valid()) {
            return noStore(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Código de la app inválido o expirado.")));
        }

        userTotpService.enableTotp(user, secret, vr.acceptedTimeStep());
        removePendingTotpSecret(request, personId);

        return noStore(ResponseEntity.ok(Map.of(
                "ok", true,
                "enabled", true,
                "message", "Autenticador de celular activado correctamente."
        )));
    }

    /**
     * Prepara en el modelo la sección opcional de activación TOTP después del registro exitoso.
     *
     * <p>Solo se ejecuta si el usuario marcó la opción de activar el autenticador de celular
     * durante el registro. El secreto se guarda temporalmente en sesión hasta la confirmación.</p>
     *
     * @param form formulario de registro
     * @param createdUser usuario recién creado
     * @param request request HTTP para acceso a sesión
     * @param model modelo de la vista de registro
     */
    private void prepareOptionalTotpSetupIfRequested(UserRegisterForm form,
                                                     AppUser createdUser,
                                                     HttpServletRequest request,
                                                     Model model) {
        if (createdUser == null || !Boolean.TRUE.equals(form != null ? form.getEnableTotpNow() : Boolean.FALSE)) {
            return;
        }

        String secret = userTotpService.generateSecretBase32();
        savePendingTotpSecret(request, createdUser.getPersonId(), secret);

        model.addAttribute("showRegisterTotpSetup", true);
        model.addAttribute("registerTotpPersonId", createdUser.getPersonId());
        model.addAttribute("registerTotpSecret", secret);
        model.addAttribute("registerTotpOtpAuthUri", userTotpService.buildOtpAuthUri(createdUser.getEmail(), secret));
    }

    /**
     * Obtiene (o crea) el mapa de secretos TOTP temporales del flujo de registro en sesión.
     *
     * @param request request HTTP
     * @param createIfMissing si es {@code true}, crea el mapa cuando no exista
     * @return mapa {@code personId -> secretBase32} o {@code null} si no existe y no se crea
     */
    @SuppressWarnings("unchecked")
    private Map<Long, String> pendingTotpSecrets(HttpServletRequest request, boolean createIfMissing) {
        var session = request.getSession(createIfMissing);
        if (session == null) return null;

        Object raw = session.getAttribute(SESSION_REGISTER_PENDING_TOTP_SECRETS);
        if (raw instanceof Map<?, ?> rawMap) {
            return (Map<Long, String>) rawMap;
        }

        if (!createIfMissing) return null;
        Map<Long, String> created = new LinkedHashMap<>();
        session.setAttribute(SESSION_REGISTER_PENDING_TOTP_SECRETS, created);
        return created;
    }

    /**
     * Guarda un secreto TOTP temporal asociado al usuario recién registrado.
     *
     * @param request request HTTP
     * @param personId identificador del usuario creado
     * @param secret secreto TOTP Base32
     */
    private void savePendingTotpSecret(HttpServletRequest request, Long personId, String secret) {
        if (personId == null || secret == null || secret.isBlank()) return;
        Map<Long, String> map = pendingTotpSecrets(request, true);
        if (map != null) map.put(personId, secret);
    }

    /**
     * Recupera el secreto TOTP temporal del flujo de registro.
     *
     * @param request request HTTP
     * @param personId identificador del usuario creado
     * @return secreto Base32 pendiente o {@code null}
     */
    private String getPendingTotpSecret(HttpServletRequest request, Long personId) {
        if (personId == null) return null;
        Map<Long, String> map = pendingTotpSecrets(request, false);
        return map == null ? null : map.get(personId);
    }

    /**
     * Elimina el secreto TOTP temporal de un usuario recién registrado.
     *
     * @param request request HTTP
     * @param personId identificador del usuario creado
     */
    private void removePendingTotpSecret(HttpServletRequest request, Long personId) {
        if (personId == null) return;
        Map<Long, String> map = pendingTotpSecrets(request, false);
        if (map != null) map.remove(personId);
    }

    /**
     * Normaliza textos nulos/blancos en el flujo AJAX de confirmación TOTP.
     *
     * @param value texto a normalizar
     * @return texto recortado o cadena vacía
     */
    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Reaplica cabecera {@code no-store} a respuestas JSON del flujo de activación TOTP.
     *
     * @param response respuesta base
     * @return respuesta con política de caché deshabilitada
     */
    private static ResponseEntity<Map<String, Object>> noStore(ResponseEntity<Map<String, Object>> response) {
        return ResponseEntity.status(response.getStatusCode())
                .cacheControl(CacheControl.noStore())
                .body(response.getBody());
    }
}
