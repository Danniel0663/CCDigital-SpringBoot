package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.dto.UserRegisterForm;
import co.edu.unbosque.ccdigital.entity.AppUser;
import co.edu.unbosque.ccdigital.entity.IdType;
import co.edu.unbosque.ccdigital.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio de aplicación para el flujo de registro web del usuario final.
 *
 * <p>Mantiene intacto el comportamiento del registro con validación por correo y
 * activación opcional de TOTP, dejando al controlador como capa HTTP delgada.</p>
 */
@Service
public class UserRegistrationFlowService {

    private static final String SESSION_REGISTER_PENDING_TOTP_SECRETS = "register.user.pendingTotpSecretsByPersonId";
    private static final String SESSION_REGISTER_PENDING_FORMS = "register.user.pendingFormsByEmailToken";

    private final UserAccountService userAccountService;
    private final UserRegisterEmailOtpService userRegisterEmailOtpService;
    private final UserTotpService userTotpService;
    private final AppUserRepository appUserRepository;

    public UserRegistrationFlowService(UserAccountService userAccountService,
                                       UserRegisterEmailOtpService userRegisterEmailOtpService,
                                       UserTotpService userTotpService,
                                       AppUserRepository appUserRepository) {
        this.userAccountService = userAccountService;
        this.userRegisterEmailOtpService = userRegisterEmailOtpService;
        this.userTotpService = userTotpService;
        this.appUserRepository = appUserRepository;
    }

    /**
     * Inicializa atributos básicos de la vista de registro.
     */
    public void prepareForm(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new UserRegisterForm());
        }
        model.addAttribute("idTypes", IdType.values());
    }

    /**
     * Procesa el POST principal del formulario de registro.
     */
    public String register(UserRegisterForm form, Model model, HttpServletRequest request) {
        model.addAttribute("idTypes", IdType.values());
        if (isEmailVerificationConfirmationStep(form)) {
            handleEmailVerificationConfirmation(form, model, request);
            return "auth/register-user";
        }

        handleRegistrationStart(form, model, request);
        return "auth/register-user";
    }

    /**
     * Confirma activación TOTP opcional luego de registro exitoso.
     */
    public ResponseEntity<Map<String, Object>> confirmRegisterTotp(Long personId,
                                                                   String code,
                                                                   HttpServletRequest request) {
        if (personId == null) {
            return noStore(ResponseEntity.badRequest().body(Map.of("error", "personId es requerido")));
        }
        if (normalize(code).isBlank()) {
            return noStore(ResponseEntity.badRequest().body(Map.of("error", "code es requerido")));
        }

        String secret = getPendingTotpSecret(request, personId);
        if (secret == null || secret.isBlank()) {
            return noStore(ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "La configuración del autenticador expiró. Regístrate o configúralo desde tu dashboard.")));
        }

        AppUser user = appUserRepository.findById(personId).orElse(null);
        if (user == null) {
            return noStore(ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No se encontró el usuario recién registrado.")));
        }

        UserTotpService.VerificationResult vr = userTotpService.verifyCode(secret, normalize(code), null);
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
     * Reenvía código de verificación de correo para registro pendiente.
     */
    public ResponseEntity<Map<String, Object>> resendRegisterEmailOtp(String emailToken,
                                                                      HttpServletRequest request) {
        String token = normalize(emailToken);
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

    private void handleRegistrationStart(UserRegisterForm form, Model model, HttpServletRequest request) {
        UserRegisterForm safeForm = form != null ? form : new UserRegisterForm();
        String email = normalize(safeForm.getEmail());
        String password = normalize(safeForm.getPassword());
        String confirmPassword = normalize(safeForm.getConfirmPassword());
        if (email.isBlank()) {
            model.addAttribute("form", safeForm);
            model.addAttribute("error", "Correo requerido.");
            return;
        }
        if (password.isBlank()) {
            safeForm.setPassword(null);
            safeForm.setConfirmPassword(null);
            model.addAttribute("form", safeForm);
            model.addAttribute("error", "Contraseña requerida.");
            return;
        }
        if (!password.equals(confirmPassword)) {
            safeForm.setPassword(null);
            safeForm.setConfirmPassword(null);
            model.addAttribute("form", safeForm);
            model.addAttribute("error", "La confirmación de contraseña no coincide.");
            return;
        }
        if (!isStrongEnoughPassword(password)) {
            safeForm.setPassword(null);
            safeForm.setConfirmPassword(null);
            model.addAttribute("form", safeForm);
            model.addAttribute("error",
                    "La contraseña debe tener mínimo 8 caracteres e incluir letras, números y un carácter especial.");
            return;
        }

        String emailToken = UUID.randomUUID().toString();
        boolean sent = userRegisterEmailOtpService.issueCode(emailToken, email, displayName(safeForm));
        if (!sent) {
            safeForm.setPassword(null);
            safeForm.setConfirmPassword(null);
            model.addAttribute("form", safeForm);
            model.addAttribute("error", "No fue posible enviar el código de verificación al correo.");
            return;
        }

        savePendingRegisterForm(request, emailToken, copyFormForSession(safeForm));
        userRegisterEmailOtpService.invalidate(normalize(safeForm.getRegistrationEmailToken()));
        showEmailVerificationStep(model, emailToken, email, Boolean.TRUE.equals(safeForm.getEnableTotpNow()));
        model.addAttribute("form", new UserRegisterForm());
    }

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

    private boolean isEmailVerificationConfirmationStep(UserRegisterForm form) {
        return !normalize(form == null ? null : form.getRegistrationEmailToken()).isBlank()
                || !normalize(form == null ? null : form.getRegistrationEmailCode()).isBlank();
    }

    private void showEmailVerificationStep(Model model,
                                           String emailToken,
                                           String email,
                                           boolean totpWillBeOfferedAfterRegister) {
        model.addAttribute("awaitingRegisterEmailVerification", true);
        model.addAttribute("registerEmailVerificationToken", emailToken);
        model.addAttribute("registerEmailVerificationMaskedEmail", maskEmail(email));
        model.addAttribute("registerEmailVerificationNextStepTotp", totpWillBeOfferedAfterRegister);
    }

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

    private void savePendingRegisterForm(HttpServletRequest request, String emailToken, UserRegisterForm form) {
        if (emailToken == null || emailToken.isBlank() || form == null) return;
        Map<String, UserRegisterForm> map = pendingRegisterForms(request, true);
        if (map != null) map.put(emailToken, form);
    }

    private UserRegisterForm getPendingRegisterForm(HttpServletRequest request, String emailToken) {
        if (emailToken == null || emailToken.isBlank()) return null;
        Map<String, UserRegisterForm> map = pendingRegisterForms(request, false);
        return map == null ? null : map.get(emailToken);
    }

    private void removePendingRegisterForm(HttpServletRequest request, String emailToken) {
        if (emailToken == null || emailToken.isBlank()) return;
        Map<String, UserRegisterForm> map = pendingRegisterForms(request, false);
        if (map != null) map.remove(emailToken);
    }

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

    private String displayName(UserRegisterForm form) {
        String out = (normalize(form == null ? null : form.getFirstName()) + " "
                + normalize(form == null ? null : form.getLastName())).trim();
        return out.isBlank() ? normalize(form == null ? null : form.getEmail()) : out;
    }

    private String maskEmail(String email) {
        String value = normalize(email);
        int at = value.indexOf('@');
        if (at <= 1) return value;
        String local = value.substring(0, at);
        String domain = value.substring(at);
        if (local.length() <= 2) return local.charAt(0) + "*" + domain;
        return local.substring(0, 2) + "*".repeat(Math.max(1, local.length() - 2)) + domain;
    }

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

    private void savePendingTotpSecret(HttpServletRequest request, Long personId, String secret) {
        if (personId == null || secret == null || secret.isBlank()) return;
        Map<Long, String> map = pendingTotpSecrets(request, true);
        if (map != null) map.put(personId, secret);
    }

    private String getPendingTotpSecret(HttpServletRequest request, Long personId) {
        if (personId == null) return null;
        Map<Long, String> map = pendingTotpSecrets(request, false);
        return map == null ? null : map.get(personId);
    }

    private void removePendingTotpSecret(HttpServletRequest request, Long personId) {
        if (personId == null) return;
        Map<Long, String> map = pendingTotpSecrets(request, false);
        if (map != null) map.remove(personId);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static ResponseEntity<Map<String, Object>> noStore(ResponseEntity<Map<String, Object>> response) {
        return ResponseEntity.status(response.getStatusCode())
                .cacheControl(CacheControl.noStore())
                .body(response.getBody());
    }
}
