package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.entity.AppUser;
import co.edu.unbosque.ccdigital.entity.Person;
import co.edu.unbosque.ccdigital.entity.UserAccessState;
import co.edu.unbosque.ccdigital.repository.AppUserRepository;
import co.edu.unbosque.ccdigital.repository.PersonRepository;
import co.edu.unbosque.ccdigital.security.IndyUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Servicio de aplicación para el flujo de autenticación de usuario final.
 *
 * <p>Centraliza start/poll/verify/resend manteniendo el mismo contrato HTTP
 * que consumen los endpoints REST del controlador.</p>
 */
@Service
public class UserAuthFlowService {

    private static final int MAX_OTP_LOGIN_ATTEMPTS = 3;
    private static final String SESSION_EXPECTED_ID_NUMBERS = "user.auth.expectedIdNumbersByPresExId";
    private static final String SESSION_EXPECTED_EMAILS = "user.auth.expectedEmailsByPresExId";
    private static final String SESSION_PENDING_OTP_CONTEXTS = "user.auth.pendingOtpContextsByPresExId";

    private final IndyProofLoginService proofLoginService;
    private final AppUserRepository appUserRepository;
    private final PersonRepository personRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserLoginOtpService userLoginOtpService;
    private final UserTotpService userTotpService;

    public UserAuthFlowService(IndyProofLoginService proofLoginService,
                               AppUserRepository appUserRepository,
                               PersonRepository personRepository,
                               PasswordEncoder passwordEncoder,
                               UserLoginOtpService userLoginOtpService,
                               UserTotpService userTotpService) {
        this.proofLoginService = proofLoginService;
        this.appUserRepository = appUserRepository;
        this.personRepository = personRepository;
        this.passwordEncoder = passwordEncoder;
        this.userLoginOtpService = userLoginOtpService;
        this.userTotpService = userTotpService;
    }

    /**
     * Inicia el flujo de login por credencial verificable.
     */
    public ResponseEntity<Map<String, Object>> start(String email,
                                                     String password,
                                                     HttpServletRequest request) {
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email es requerido"));
        }
        if (password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "password es requerido"));
        }
        String emailNorm = email.trim();

        AppUser appUser = appUserRepository
                .findByEmailIgnoreCase(emailNorm)
                .filter(this::hasUserRole)
                .orElse(null);

        if (appUser == null
                || appUser.getPasswordHash() == null
                || appUser.getPasswordHash().isBlank()
                || !passwordEncoder.matches(password, appUser.getPasswordHash())) {
            return unauthorized("Correo o clave inválidos");
        }

        String accessBlockMessage = resolveAccessBlockMessage(appUser);
        if (accessBlockMessage != null) {
            return forbidden(accessBlockMessage);
        }

        String idNumberFromDb = findIdNumberByUser(appUser);
        if (idNumberFromDb.isBlank()) {
            return unauthorized("No se encontró cédula asociada al correo ingresado");
        }

        Map<String, Object> startResp = proofLoginService.startLoginByIdNumber(idNumberFromDb);

        Object presExId = startResp.get("presExId");
        if (presExId == null) presExId = startResp.get("pres_ex_id");
        if (presExId == null) presExId = startResp.get("presentation_exchange_id");
        String presExIdValue = presExId != null ? String.valueOf(presExId).trim() : "";

        if (presExIdValue.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .cacheControl(CacheControl.noStore())
                    .body(Map.of("error", "No se recibió presExId desde el verificador Indy"));
        }

        saveExpectedIdNumber(request, presExIdValue, idNumberFromDb);
        saveExpectedEmail(request, presExIdValue, emailNorm);
        removePendingOtpContext(request, presExIdValue);
        userLoginOtpService.invalidate(presExIdValue);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("presExId", presExIdValue);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(out);
    }

    /**
     * Consulta estado del intercambio y prepara segundo factor cuando aplique.
     */
    public ResponseEntity<Map<String, Object>> poll(String presExId,
                                                    HttpServletRequest request) {
        if (presExId == null || presExId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "presExId es requerido"));
        }
        String presExIdNorm = presExId.trim();
        String expectedIdNumber = getExpectedIdNumber(request, presExIdNorm);
        if (expectedIdNumber == null || expectedIdNumber.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .cacheControl(CacheControl.noStore())
                    .body(Map.of("error", "La sesión de autenticación no es válida o expiró"));
        }

        Map<String, Object> status = proofLoginService.getProofStatus(presExIdNorm);

        boolean done = Boolean.TRUE.equals(status.get("done"));
        boolean verified = Boolean.TRUE.equals(status.get("verified"));

        Map<String, Object> out = new LinkedHashMap<>(status);

        if (done && verified) {
            Map<String, String> attrs = proofLoginService.getVerifiedResultWithAttrs(presExIdNorm);
            String verifiedIdNumber = normalize(attrs.get("id_number"));

            if (!expectedIdNumber.equals(verifiedIdNumber)) {
                removeExpectedIdNumber(request, presExIdNorm);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .cacheControl(CacheControl.noStore())
                        .body(Map.of(
                                "authenticated", false,
                                "error", "La credencial verificada no coincide con el usuario ingresado"
                        ));
            }

            PendingOtpContext pending = getPendingOtpContext(request, presExIdNorm);
            if (pending == null) {
                String loginEmail = getExpectedEmail(request, presExIdNorm);
                if (loginEmail == null || loginEmail.isBlank()) {
                    removeExpectedIdNumber(request, presExIdNorm);
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .cacheControl(CacheControl.noStore())
                            .body(Map.of("error", "La sesión de autenticación expiró. Intenta de nuevo."));
                }

                AppUser governanceUser = findUserByEmail(loginEmail);
                String accessBlockMessage = resolveAccessBlockMessage(governanceUser);
                if (accessBlockMessage != null) {
                    removeExpectedIdNumber(request, presExIdNorm);
                    removeExpectedEmail(request, presExIdNorm);
                    removePendingOtpContext(request, presExIdNorm);
                    userLoginOtpService.invalidate(presExIdNorm);
                    return forbidden(accessBlockMessage);
                }

                pending = buildPendingOtpContext(attrs, loginEmail);
                AppUser loginUser = findActiveUserByEmail(loginEmail);
                if (loginUser != null) {
                    pending.setPersonId(loginUser.getPersonId());
                }

                if (loginUser != null && userTotpService.isTotpEnabled(loginUser)) {
                    pending.setSecondFactorMethod("totp");
                } else {
                    pending.setSecondFactorMethod("email");
                    boolean sent = userLoginOtpService.issueCode(
                            presExIdNorm,
                            loginEmail,
                            displayNameFromPending(pending)
                    );
                    if (!sent) {
                        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                                .cacheControl(CacheControl.noStore())
                                .body(Map.of("error", "No fue posible enviar el código de verificación al correo."));
                    }
                }
                savePendingOtpContext(request, presExIdNorm, pending);
            }

            out.put("authenticated", false);
            out.put("otpRequired", true);
            if (isTotpFactor(pending)) {
                out.put("otpMethod", "totp");
                out.put("allowEmailFallback", true);
                out.put("maskedEmail", maskEmail(pending.getLoginEmail()));
                out.put("message", "Ingresa el código de tu app de autenticación.");
            } else {
                out.put("otpMethod", "email");
                out.put("maskedEmail", maskEmail(pending.getLoginEmail()));
                out.put("message", "Se envió un código de verificación a tu correo.");
            }
        } else {
            out.put("authenticated", false);
            if (done) {
                removeExpectedIdNumber(request, presExIdNorm);
                removeExpectedEmail(request, presExIdNorm);
                removePendingOtpContext(request, presExIdNorm);
                userLoginOtpService.invalidate(presExIdNorm);
            }
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(out);
    }

    /**
     * Verifica código de segundo factor y completa la autenticación de sesión.
     */
    public ResponseEntity<Map<String, Object>> verifyOtp(String presExId,
                                                         String code,
                                                         HttpServletRequest request) {
        String presExIdNorm = normalize(presExId);
        String codeNorm = normalize(code);

        if (presExIdNorm.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "presExId es requerido"));
        }
        if (codeNorm.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "code es requerido"));
        }

        PendingOtpContext pending = getPendingOtpContext(request, presExIdNorm);
        if (pending == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .cacheControl(CacheControl.noStore())
                    .body(Map.of("error", "La sesión de validación expiró. Intenta iniciar sesión nuevamente."));
        }

        AppUser governanceUser = pending.getPersonId() != null
                ? findUserByPersonId(pending.getPersonId())
                : findUserByEmail(pending.getLoginEmail());
        String accessBlockMessage = resolveAccessBlockMessage(governanceUser);
        if (accessBlockMessage != null) {
            removeExpectedIdNumber(request, presExIdNorm);
            removeExpectedEmail(request, presExIdNorm);
            removePendingOtpContext(request, presExIdNorm);
            userLoginOtpService.invalidate(presExIdNorm);
            return forbidden(accessBlockMessage);
        }

        boolean ok;
        if (isTotpFactor(pending)) {
            AppUser loginUser = pending.getPersonId() != null
                    ? findActiveUserByPersonId(pending.getPersonId())
                    : findActiveUserByEmail(pending.getLoginEmail());
            ok = userTotpService.verifyLoginCodeAndMark(loginUser, codeNorm);
        } else {
            ok = userLoginOtpService.verifyCode(presExIdNorm, codeNorm);
        }
        if (!ok) {
            int failed = Math.max(0, pending.getFailedOtpAttempts()) + 1;
            pending.setFailedOtpAttempts(failed);

            int remaining = MAX_OTP_LOGIN_ATTEMPTS - failed;
            if (remaining <= 0) {
                userLoginOtpService.sendSuspiciousLoginAlert(
                        pending.getLoginEmail(),
                        displayNameFromPending(pending)
                );
                removeExpectedIdNumber(request, presExIdNorm);
                removeExpectedEmail(request, presExIdNorm);
                removePendingOtpContext(request, presExIdNorm);
                userLoginOtpService.invalidate(presExIdNorm);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .cacheControl(CacheControl.noStore())
                        .body(Map.of(
                                "error", "Se agotaron los intentos de verificación. Debes iniciar sesión nuevamente.",
                                "restartLogin", true
                        ));
            }

            savePendingOtpContext(request, presExIdNorm, pending);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .cacheControl(CacheControl.noStore())
                    .body(Map.of(
                            "error", (isTotpFactor(pending)
                                    ? "Código de la app inválido."
                                    : "Código inválido.")
                                    + " Te quedan " + remaining + " intento(s).",
                            "attemptsRemaining", remaining
                    ));
        }

        IndyUserPrincipal principal = new IndyUserPrincipal(
                pending.getIdType(),
                pending.getIdNumber(),
                pending.getFirstName(),
                pending.getLastName(),
                pending.getProfileEmail()
        );
        authenticateInSession(request, principal);

        removeExpectedIdNumber(request, presExIdNorm);
        removeExpectedEmail(request, presExIdNorm);
        removePendingOtpContext(request, presExIdNorm);
        userLoginOtpService.invalidate(presExIdNorm);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of(
                        "authenticated", true,
                        "redirectUrl", "/user/dashboard",
                        "displayName", principal.getDisplayName()
                ));
    }

    /**
     * Reenvía OTP por correo o activa fallback desde TOTP a correo.
     */
    public ResponseEntity<Map<String, Object>> resendOtp(String presExId,
                                                         HttpServletRequest request) {
        String presExIdNorm = normalize(presExId);
        if (presExIdNorm.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "presExId es requerido"));
        }

        PendingOtpContext pending = getPendingOtpContext(request, presExIdNorm);
        if (pending == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .cacheControl(CacheControl.noStore())
                    .body(Map.of("error", "La sesión de validación expiró. Inicia sesión nuevamente."));
        }

        AppUser governanceUser = pending.getPersonId() != null
                ? findUserByPersonId(pending.getPersonId())
                : findUserByEmail(pending.getLoginEmail());
        String accessBlockMessage = resolveAccessBlockMessage(governanceUser);
        if (accessBlockMessage != null) {
            removeExpectedIdNumber(request, presExIdNorm);
            removeExpectedEmail(request, presExIdNorm);
            removePendingOtpContext(request, presExIdNorm);
            userLoginOtpService.invalidate(presExIdNorm);
            return forbidden(accessBlockMessage);
        }
        if (isTotpFactor(pending)) {
            boolean sentFallback = userLoginOtpService.issueCode(
                    presExIdNorm,
                    pending.getLoginEmail(),
                    displayNameFromPending(pending)
            );
            if (!sentFallback) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .cacheControl(CacheControl.noStore())
                        .body(Map.of("error", "No fue posible enviar el código de respaldo al correo."));
            }

            pending.setSecondFactorMethod("email");
            savePendingOtpContext(request, presExIdNorm, pending);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(Map.of(
                            "ok", true,
                            "otpMethod", "email",
                            "maskedEmail", maskEmail(pending.getLoginEmail()),
                            "message", "Enviamos un código de respaldo a tu correo para continuar el ingreso."
                    ));
        }

        boolean sent = userLoginOtpService.issueCode(
                presExIdNorm,
                pending.getLoginEmail(),
                displayNameFromPending(pending)
        );
        if (!sent) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .cacheControl(CacheControl.noStore())
                    .body(Map.of("error", "No fue posible reenviar el código. Intenta nuevamente."));
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of(
                        "ok", true,
                        "otpMethod", "email",
                        "maskedEmail", maskEmail(pending.getLoginEmail()),
                        "message", "Si aplica, se reenvió un código a tu correo."
                ));
    }

    private boolean hasUserRole(AppUser appUser) {
        String role = normalize(appUser.getRole());
        if (role.startsWith("ROLE_")) {
            role = role.substring("ROLE_".length());
        }
        return "USER".equalsIgnoreCase(role) || "USUARIO".equalsIgnoreCase(role);
    }

    private AppUser findActiveUserByEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return appUserRepository.findByEmailIgnoreCase(email.trim())
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .filter(this::hasUserRole)
                .orElse(null);
    }

    private AppUser findUserByEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return appUserRepository.findByEmailIgnoreCase(email.trim())
                .filter(this::hasUserRole)
                .orElse(null);
    }

    private AppUser findActiveUserByPersonId(Long personId) {
        if (personId == null) {
            return null;
        }
        return appUserRepository.findById(personId)
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .filter(this::hasUserRole)
                .orElse(null);
    }

    private AppUser findUserByPersonId(Long personId) {
        if (personId == null) {
            return null;
        }
        return appUserRepository.findById(personId)
                .filter(this::hasUserRole)
                .orElse(null);
    }

    private String findIdNumberByUser(AppUser appUser) {
        Long personId = appUser.getPersonId();
        if (personId == null) {
            return "";
        }
        return personRepository.findById(personId)
                .map(Person::getIdNumber)
                .map(UserAuthFlowService::normalize)
                .orElse("");
    }

    private ResponseEntity<Map<String, Object>> unauthorized(String message) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .cacheControl(CacheControl.noStore())
                .body(Map.of("error", message));
    }

    private ResponseEntity<Map<String, Object>> forbidden(String message) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .cacheControl(CacheControl.noStore())
                .body(Map.of("error", message));
    }

    /**
     * Determina si el usuario puede continuar autenticación según estado de acceso.
     */
    private String resolveAccessBlockMessage(AppUser appUser) {
        if (appUser == null) {
            return "No se encontró la cuenta de usuario para autenticación.";
        }
        if (!Boolean.TRUE.equals(appUser.getIsActive())) {
            return "Tu cuenta está inhabilitada. Contacta al administrador.";
        }
        UserAccessState state = appUser.getAccessState() == null ? UserAccessState.ENABLED : appUser.getAccessState();
        if (state == UserAccessState.SUSPENDED) {
            return "Tu cuenta está suspendida temporalmente. Contacta al administrador.";
        }
        if (state == UserAccessState.DISABLED) {
            return "Tu cuenta está inhabilitada. Contacta al administrador.";
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> expectedIdNumbers(HttpServletRequest request, boolean createIfMissing) {
        var session = request.getSession(createIfMissing);
        if (session == null) {
            return null;
        }

        Object raw = session.getAttribute(SESSION_EXPECTED_ID_NUMBERS);
        if (raw instanceof Map<?, ?> rawMap) {
            return (Map<String, String>) rawMap;
        }

        if (!createIfMissing) {
            return null;
        }

        Map<String, String> created = new LinkedHashMap<>();
        session.setAttribute(SESSION_EXPECTED_ID_NUMBERS, created);
        return created;
    }

    private void saveExpectedIdNumber(HttpServletRequest request, String presExId, String idNumber) {
        Map<String, String> expected = expectedIdNumbers(request, true);
        if (expected != null) {
            expected.put(presExId, idNumber);
        }
    }

    private String getExpectedIdNumber(HttpServletRequest request, String presExId) {
        Map<String, String> expected = expectedIdNumbers(request, false);
        return expected == null ? null : normalize(expected.get(presExId));
    }

    private void removeExpectedIdNumber(HttpServletRequest request, String presExId) {
        Map<String, String> expected = expectedIdNumbers(request, false);
        if (expected == null) {
            return;
        }
        expected.remove(presExId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> expectedEmails(HttpServletRequest request, boolean createIfMissing) {
        var session = request.getSession(createIfMissing);
        if (session == null) return null;
        Object raw = session.getAttribute(SESSION_EXPECTED_EMAILS);
        if (raw instanceof Map<?, ?> rawMap) {
            return (Map<String, String>) rawMap;
        }
        if (!createIfMissing) return null;
        Map<String, String> created = new LinkedHashMap<>();
        session.setAttribute(SESSION_EXPECTED_EMAILS, created);
        return created;
    }

    private void saveExpectedEmail(HttpServletRequest request, String presExId, String email) {
        Map<String, String> expected = expectedEmails(request, true);
        if (expected != null) expected.put(presExId, normalize(email));
    }

    private String getExpectedEmail(HttpServletRequest request, String presExId) {
        Map<String, String> expected = expectedEmails(request, false);
        return expected == null ? null : normalize(expected.get(presExId));
    }

    private void removeExpectedEmail(HttpServletRequest request, String presExId) {
        Map<String, String> expected = expectedEmails(request, false);
        if (expected != null) expected.remove(presExId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, PendingOtpContext> pendingOtpContexts(HttpServletRequest request, boolean createIfMissing) {
        var session = request.getSession(createIfMissing);
        if (session == null) return null;
        Object raw = session.getAttribute(SESSION_PENDING_OTP_CONTEXTS);
        if (raw instanceof Map<?, ?> rawMap) {
            return (Map<String, PendingOtpContext>) rawMap;
        }
        if (!createIfMissing) return null;
        Map<String, PendingOtpContext> created = new LinkedHashMap<>();
        session.setAttribute(SESSION_PENDING_OTP_CONTEXTS, created);
        return created;
    }

    private void savePendingOtpContext(HttpServletRequest request, String presExId, PendingOtpContext ctx) {
        Map<String, PendingOtpContext> map = pendingOtpContexts(request, true);
        if (map != null) map.put(presExId, ctx);
    }

    private PendingOtpContext getPendingOtpContext(HttpServletRequest request, String presExId) {
        Map<String, PendingOtpContext> map = pendingOtpContexts(request, false);
        return map == null ? null : map.get(presExId);
    }

    private void removePendingOtpContext(HttpServletRequest request, String presExId) {
        Map<String, PendingOtpContext> map = pendingOtpContexts(request, false);
        if (map != null) map.remove(presExId);
    }

    private PendingOtpContext buildPendingOtpContext(Map<String, String> attrs, String loginEmail) {
        PendingOtpContext ctx = new PendingOtpContext();
        ctx.setIdType(attrs.getOrDefault("id_type", ""));
        ctx.setIdNumber(attrs.getOrDefault("id_number", ""));
        ctx.setFirstName(attrs.getOrDefault("first_name", ""));
        ctx.setLastName(attrs.getOrDefault("last_name", ""));
        ctx.setProfileEmail(attrs.getOrDefault("email", ""));
        ctx.setLoginEmail(loginEmail);
        return ctx;
    }

    private boolean isTotpFactor(PendingOtpContext ctx) {
        return "totp".equalsIgnoreCase(normalize(ctx == null ? null : ctx.getSecondFactorMethod()));
    }

    private void authenticateInSession(HttpServletRequest request, IndyUserPrincipal principal) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                context
        );
    }

    private static String displayNameFromPending(PendingOtpContext ctx) {
        if (ctx == null) return "";
        String fn = normalize(ctx.getFirstName());
        String ln = normalize(ctx.getLastName());
        String out = (fn + " " + ln).trim();
        return out.isBlank() ? normalize(ctx.getLoginEmail()) : out;
    }

    private static String maskEmail(String email) {
        String value = normalize(email);
        int at = value.indexOf('@');
        if (at <= 1) return value;
        String local = value.substring(0, at);
        String domain = value.substring(at);
        if (local.length() <= 2) return local.charAt(0) + "*" + domain;
        return local.substring(0, 2) + "*".repeat(Math.max(1, local.length() - 2)) + domain;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Contexto temporal pendiente de autenticación final mientras se valida OTP.
     */
    public static class PendingOtpContext {
        private Long personId;
        private String idType;
        private String idNumber;
        private String firstName;
        private String lastName;
        private String profileEmail;
        private String loginEmail;
        private String secondFactorMethod;
        private int failedOtpAttempts;

        public Long getPersonId() { return personId; }
        public void setPersonId(Long personId) { this.personId = personId; }
        public String getIdType() { return idType; }
        public void setIdType(String idType) { this.idType = idType; }
        public String getIdNumber() { return idNumber; }
        public void setIdNumber(String idNumber) { this.idNumber = idNumber; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public String getProfileEmail() { return profileEmail; }
        public void setProfileEmail(String profileEmail) { this.profileEmail = profileEmail; }
        public String getLoginEmail() { return loginEmail; }
        public void setLoginEmail(String loginEmail) { this.loginEmail = loginEmail; }
        public String getSecondFactorMethod() { return secondFactorMethod; }
        public void setSecondFactorMethod(String secondFactorMethod) { this.secondFactorMethod = secondFactorMethod; }
        public int getFailedOtpAttempts() { return failedOtpAttempts; }
        public void setFailedOtpAttempts(int failedOtpAttempts) { this.failedOtpAttempts = failedOtpAttempts; }
    }
}
