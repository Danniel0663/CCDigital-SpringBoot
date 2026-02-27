package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.dto.UserRegisterForm;
import co.edu.unbosque.ccdigital.entity.AppUser;
import co.edu.unbosque.ccdigital.entity.IdType;
import co.edu.unbosque.ccdigital.repository.AppUserRepository;
import co.edu.unbosque.ccdigital.service.UserAccountService;
import co.edu.unbosque.ccdigital.service.UserRegisterEmailOtpService;
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
import java.util.UUID;

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
 * Antes de crear la cuenta, verifica el correo ingresado mediante un código OTP enviado por email.
 * Solo tras validar ese código se crea el usuario en {@code users}.
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
    private static final String SESSION_REGISTER_PENDING_FORMS = "register.user.pendingFormsByEmailToken";

    private final UserAccountService userAccountService;
    private final UserRegisterEmailOtpService userRegisterEmailOtpService;
    private final UserTotpService userTotpService;
    private final AppUserRepository appUserRepository;

    /**
     * Constructor del controlador.
     *
     * @param userAccountService servicio de registro de usuarios
     * @param userRegisterEmailOtpService servicio OTP para verificación de correo antes del registro
     * @param userTotpService servicio de generación/validación TOTP para activación opcional
     * @param appUserRepository repositorio de usuarios para confirmar activación post-registro
     */
    public UserRegistrationController(UserAccountService userAccountService,
                                      UserRegisterEmailOtpService userRegisterEmailOtpService,
                                      UserTotpService userTotpService,
                                      AppUserRepository appUserRepository) {
        this.userAccountService = userAccountService;
        this.userRegisterEmailOtpService = userRegisterEmailOtpService;
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
     * Request AJAX para reenviar código de verificación de correo en registro.
     */
    public static class RegisterEmailResendRequest {
        private String emailToken;

        public String getEmailToken() {
            return emailToken;
        }

        public void setEmailToken(String emailToken) {
            this.emailToken = emailToken;
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
        if (isEmailVerificationConfirmationStep(form)) {
            handleEmailVerificationConfirmation(form, model, request);
            return "auth/register-user";
        }

        handleRegistrationStart(form, model, request);

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
     * Reenvía el código OTP de verificación de correo para un registro pendiente.
     *
     * @param req request con token del flujo de verificación de correo
     * @param request request HTTP para leer formulario pendiente en sesión
     * @return respuesta JSON con estado de reenvío
     */
    @PostMapping("/email-otp/resend")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> resendRegisterEmailOtp(@RequestBody RegisterEmailResendRequest req,
                                                                       HttpServletRequest request) {
        String token = normalize(req == null ? null : req.getEmailToken());
        if (token.isBlank()) {
            return noStore(ResponseEntity.badRequest().body(Map.of("error", "emailToken es requerido")));
        }

        UserRegisterForm pending = getPendingRegisterForm(request, token);
        if (pending == null) {
            return noStore(ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "La verificación expiró. Completa nuevamente el formulario.")));
        }

        boolean sent = userRegisterEmailOtpService.issueCode(
                token,
                normalize(pending.getEmail()),
                displayName(pending)
        );
        if (!sent) {
            return noStore(ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "No fue posible reenviar el código. Intenta nuevamente.")));
        }

        return noStore(ResponseEntity.ok(Map.of(
                "ok", true,
                "maskedEmail", maskEmail(pending.getEmail()),
                "message", "Si aplica, se reenvió el código al correo registrado."
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
     * Inicia la verificación de correo previa a la creación del usuario y deja el formulario pendiente en sesión.
     *
     * <p>No crea todavía el usuario en base de datos. Solo envía el código OTP al correo,
     * guarda el formulario en sesión y renderiza la vista del paso de confirmación.</p>
     *
     * @param form formulario capturado
     * @param model modelo de la vista
     * @param request request HTTP para acceso a sesión
     */
    private void handleRegistrationStart(UserRegisterForm form, Model model, HttpServletRequest request) {
        String email = normalize(form == null ? null : form.getEmail());
        String password = normalize(form == null ? null : form.getPassword());
        String confirmPassword = normalize(form == null ? null : form.getConfirmPassword());
        if (email.isBlank()) {
            model.addAttribute("form", form);
            model.addAttribute("error", "Correo requerido.");
            return;
        }
        // Validación temprana para no enviar OTP cuando la contraseña no cumple política.
        if (password.isBlank()) {
            form.setPassword(null);
            form.setConfirmPassword(null);
            model.addAttribute("form", form);
            model.addAttribute("error", "Contraseña requerida.");
            return;
        }
        if (!password.equals(confirmPassword)) {
            form.setPassword(null);
            form.setConfirmPassword(null);
            model.addAttribute("form", form);
            model.addAttribute("error", "La confirmación de contraseña no coincide.");
            return;
        }
        if (!isStrongEnoughPassword(password)) {
            form.setPassword(null);
            form.setConfirmPassword(null);
            model.addAttribute("form", form);
            model.addAttribute("error",
                    "La contraseña debe tener mínimo 8 caracteres e incluir letras, números y un carácter especial.");
            return;
        }

        String emailToken = UUID.randomUUID().toString();
        boolean sent = userRegisterEmailOtpService.issueCode(emailToken, email, displayName(form));
        if (!sent) {
            form.setPassword(null);
            form.setConfirmPassword(null);
            model.addAttribute("form", form);
            model.addAttribute("error", "No fue posible enviar el código de verificación al correo.");
            return;
        }

        savePendingRegisterForm(request, emailToken, copyFormForSession(form));
        userRegisterEmailOtpService.invalidate(normalize(form.getRegistrationEmailToken()));
        showEmailVerificationStep(model, emailToken, email, Boolean.TRUE.equals(form.getEnableTotpNow()));
        model.addAttribute("form", new UserRegisterForm());
    }

    /**
     * Regla de complejidad mínima para contraseña de registro.
     *
     * <p>Exige al menos 8 caracteres, con una letra, un número y un carácter especial.</p>
     *
     * @param pwd contraseña en texto plano ya normalizada
     * @return {@code true} si cumple la política
     */
    private boolean isStrongEnoughPassword(String pwd) {
        if (pwd == null || pwd.isBlank()) return false;
        if (pwd.length() < 8) return false;
        boolean hasLetter = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        for (char c : pwd.toCharArray()) {
            if (Character.isLetter(c)) hasLetter = true;
            if (Character.isDigit(c)) hasDigit = true;
            if (!Character.isLetterOrDigit(c)) hasSpecial = true;
        }
        return hasLetter && hasDigit && hasSpecial;
    }

    /**
     * Completa el registro después de validar el código enviado al correo.
     *
     * <p>Recupera el formulario pendiente desde sesión, valida el OTP del correo y solo después
     * delega la creación del usuario al servicio de negocio.</p>
     *
     * @param form formulario del paso de confirmación (token + código)
     * @param model modelo de la vista
     * @param request request HTTP para acceso a sesión
     */
    private void handleEmailVerificationConfirmation(UserRegisterForm form, Model model, HttpServletRequest request) {
        String emailToken = normalize(form == null ? null : form.getRegistrationEmailToken());
        String code = normalize(form == null ? null : form.getRegistrationEmailCode());

        if (emailToken.isBlank()) {
            model.addAttribute("error", "La verificación de correo expiró. Completa el formulario nuevamente.");
            model.addAttribute("form", new UserRegisterForm());
            return;
        }

        UserRegisterForm pendingForm = getPendingRegisterForm(request, emailToken);
        if (pendingForm == null) {
            model.addAttribute("error", "La verificación de correo expiró. Completa el formulario nuevamente.");
            model.addAttribute("form", new UserRegisterForm());
            return;
        }

        if (code.isBlank()) {
            showEmailVerificationStep(model, emailToken, pendingForm.getEmail(), Boolean.TRUE.equals(pendingForm.getEnableTotpNow()));
            model.addAttribute("verifyEmailError", "Ingresa el código enviado al correo.");
            model.addAttribute("form", new UserRegisterForm());
            return;
        }

        boolean ok = userRegisterEmailOtpService.verifyCode(emailToken, code);
        if (!ok) {
            showEmailVerificationStep(model, emailToken, pendingForm.getEmail(), Boolean.TRUE.equals(pendingForm.getEnableTotpNow()));
            model.addAttribute("verifyEmailError", "Código inválido o expirado.");
            model.addAttribute("form", new UserRegisterForm());
            return;
        }

        try {
            AppUser createdUser = userAccountService.registerFromExistingPerson(pendingForm);
            removePendingRegisterForm(request, emailToken);
            userRegisterEmailOtpService.invalidate(emailToken);

            model.addAttribute("success", "Usuario creado correctamente y correo verificado.");
            model.addAttribute("createdEmail", createdUser.getEmail());
            prepareOptionalTotpSetupIfRequested(pendingForm, createdUser, request, model);
            model.addAttribute("form", new UserRegisterForm());
        } catch (IllegalArgumentException ex) {
            removePendingRegisterForm(request, emailToken);
            userRegisterEmailOtpService.invalidate(emailToken);
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("form", new UserRegisterForm());
        }
    }

    /**
     * Determina si el POST actual corresponde al paso de confirmación del código de correo.
     *
     * @param form formulario recibido
     * @return {@code true} si contiene token/código del paso de verificación
     */
    private boolean isEmailVerificationConfirmationStep(UserRegisterForm form) {
        return !normalize(form == null ? null : form.getRegistrationEmailToken()).isBlank()
                || !normalize(form == null ? null : form.getRegistrationEmailCode()).isBlank();
    }

    /**
     * Prepara los atributos de modelo para mostrar el paso de validación de correo.
     *
     * @param model modelo de Spring MVC
     * @param emailToken token temporal del flujo de verificación
     * @param email correo capturado (se mostrará enmascarado)
     * @param totpWillBeOfferedAfterRegister indicador de si el usuario marcó activación TOTP opcional
     */
    private void showEmailVerificationStep(Model model,
                                           String emailToken,
                                           String email,
                                           boolean totpWillBeOfferedAfterRegister) {
        model.addAttribute("awaitingRegisterEmailVerification", true);
        model.addAttribute("registerEmailVerificationToken", emailToken);
        model.addAttribute("registerEmailVerificationMaskedEmail", maskEmail(email));
        model.addAttribute("registerEmailVerificationNextStepTotp", totpWillBeOfferedAfterRegister);
    }

    /**
     * Obtiene (o crea) el mapa de formularios pendientes del registro en sesión.
     *
     * <p>La llave es el token temporal enviado al usuario para verificar el correo.</p>
     *
     * @param request request HTTP
     * @param createIfMissing si es {@code true}, crea el mapa cuando no exista
     * @return mapa de formularios pendientes por token o {@code null}
     */
    @SuppressWarnings("unchecked")
    private Map<String, UserRegisterForm> pendingRegisterForms(HttpServletRequest request, boolean createIfMissing) {
        var session = request.getSession(createIfMissing);
        if (session == null) return null;

        Object raw = session.getAttribute(SESSION_REGISTER_PENDING_FORMS);
        if (raw instanceof Map<?, ?> rawMap) {
            return (Map<String, UserRegisterForm>) rawMap;
        }

        if (!createIfMissing) return null;
        Map<String, UserRegisterForm> created = new LinkedHashMap<>();
        session.setAttribute(SESSION_REGISTER_PENDING_FORMS, created);
        return created;
    }

    /**
     * Guarda en sesión el formulario completo pendiente de validación de correo.
     *
     * @param request request HTTP
     * @param emailToken token temporal del flujo
     * @param form copia del formulario de registro
     */
    private void savePendingRegisterForm(HttpServletRequest request, String emailToken, UserRegisterForm form) {
        if (emailToken == null || emailToken.isBlank() || form == null) return;
        Map<String, UserRegisterForm> map = pendingRegisterForms(request, true);
        if (map != null) map.put(emailToken, form);
    }

    /**
     * Recupera el formulario de registro pendiente asociado a un token de verificación de correo.
     *
     * @param request request HTTP
     * @param emailToken token temporal del flujo
     * @return formulario pendiente o {@code null}
     */
    private UserRegisterForm getPendingRegisterForm(HttpServletRequest request, String emailToken) {
        if (emailToken == null || emailToken.isBlank()) return null;
        Map<String, UserRegisterForm> map = pendingRegisterForms(request, false);
        return map == null ? null : map.get(emailToken);
    }

    /**
     * Elimina de sesión un formulario pendiente del flujo de verificación de correo.
     *
     * @param request request HTTP
     * @param emailToken token temporal del flujo
     */
    private void removePendingRegisterForm(HttpServletRequest request, String emailToken) {
        if (emailToken == null || emailToken.isBlank()) return;
        Map<String, UserRegisterForm> map = pendingRegisterForms(request, false);
        if (map != null) map.remove(emailToken);
    }

    /**
     * Crea una copia del formulario para persistirla temporalmente en sesión.
     *
     * <p>Se evita reutilizar directamente el mismo objeto del binding web para no mezclar estados
     * entre pasos del flujo de registro.</p>
     *
     * @param src formulario original
     * @return copia con los datos requeridos para completar el registro tras validar el correo
     */
    private UserRegisterForm copyFormForSession(UserRegisterForm src) {
        UserRegisterForm copy = new UserRegisterForm();
        if (src == null) return copy;
        copy.setIdType(src.getIdType());
        copy.setIdNumber(src.getIdNumber());
        copy.setFirstName(src.getFirstName());
        copy.setLastName(src.getLastName());
        copy.setEmail(src.getEmail());
        copy.setPhone(src.getPhone());
        copy.setBirthdate(src.getBirthdate());
        copy.setPassword(src.getPassword());
        copy.setConfirmPassword(src.getConfirmPassword());
        copy.setEnableTotpNow(src.getEnableTotpNow());
        return copy;
    }

    /**
     * Construye un nombre de visualización para el correo OTP de registro.
     *
     * @param form formulario de registro
     * @return nombre/apellidos si existen; de lo contrario el correo
     */
    private String displayName(UserRegisterForm form) {
        String out = (normalize(form == null ? null : form.getFirstName()) + " "
                + normalize(form == null ? null : form.getLastName())).trim();
        return out.isBlank() ? normalize(form == null ? null : form.getEmail()) : out;
    }

    /**
     * Enmascara el correo para mostrarlo en UI durante el paso de verificación.
     *
     * @param email correo capturado
     * @return correo parcialmente enmascarado
     */
    private String maskEmail(String email) {
        String value = normalize(email);
        int at = value.indexOf('@');
        if (at <= 1) return value;
        String local = value.substring(0, at);
        String domain = value.substring(at);
        if (local.length() <= 2) return local.charAt(0) + "*" + domain;
        return local.substring(0, 2) + "*".repeat(Math.max(1, local.length() - 2)) + domain;
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
