package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.entity.AppUser;
import co.edu.unbosque.ccdigital.entity.Person;
import co.edu.unbosque.ccdigital.repository.AppUserRepository;
import co.edu.unbosque.ccdigital.repository.PersonRepository;
import co.edu.unbosque.ccdigital.security.IndyUserPrincipal;
import co.edu.unbosque.ccdigital.service.IndyProofLoginService;
import co.edu.unbosque.ccdigital.service.UserLoginOtpService;
import co.edu.unbosque.ccdigital.service.UserTotpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controlador REST para autenticación de usuario final mediante prueba verificable (Indy/Aries).
 *
 * <p>
 * Implementa un flujo tipo “start/poll” para iniciar una solicitud de prueba y consultar el estado
 * hasta confirmar la verificación. Al verificarse, establece el contexto de seguridad en sesión.
 * </p>
 *
 * @since 3.0
 */
@RestController
@RequestMapping("/user/auth")
public class UserAuthController {

    /**
     * Número máximo de intentos permitidos para validar segundo factor en un mismo flujo de login.
     */
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

    /**
     * Constructor del controlador.
     *
     * @param proofLoginService servicio encargado del flujo de proof/login
     * @param appUserRepository repositorio de usuarios de aplicación
     * @param personRepository repositorio de personas
     * @param passwordEncoder comparador de hash de contraseñas (BCrypt)
     * @param userLoginOtpService servicio de envío/validación de OTP por correo para login
     * @param userTotpService servicio TOTP para MFA mediante app autenticadora
     */
    public UserAuthController(IndyProofLoginService proofLoginService,
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
     * Request para iniciar el flujo de autenticación por proof.
     *
     * @since 3.0
     */
    public static class StartRequest {

        private String email;
        private String password;

        /**
         * Correo para autenticación contra base de datos.
         *
         * @return correo
         */
        public String getEmail() {
            return email;
        }

        /**
         * Establece el correo para autenticación local.
         *
         * @param email correo ingresado
         */
        public void setEmail(String email) {
            this.email = email;
        }

        /**
         * Clave en texto plano para validación contra hash almacenado.
         *
         * @return clave
         */
        public String getPassword() {
            return password;
        }

        /**
         * Establece la clave para autenticación local.
         *
         * @param password clave ingresada
         */
        public void setPassword(String password) {
            this.password = password;
        }

    }

    /**
     * Request para validar el segundo factor por correo durante el login.
     */
    public static class OtpVerifyRequest {
        private String presExId;
        private String code;

        public String getPresExId() {
            return presExId;
        }

        public void setPresExId(String presExId) {
            this.presExId = presExId;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }
    }

    /**
     * Request para reenviar el código OTP por correo durante login.
     */
    public static class OtpResendRequest {
        private String presExId;

        public String getPresExId() {
            return presExId;
        }

        public void setPresExId(String presExId) {
            this.presExId = presExId;
        }
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
        /**
         * Intentos fallidos acumulados del segundo factor (correo o TOTP) para este contexto temporal.
         */
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

    /**
     * Inicia el flujo de autenticación por proof para el usuario identificado por correo.
     *
     * @param req request con correo y clave
     * @return respuesta con el identificador de intercambio de presentación (presExId)
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start(@RequestBody StartRequest req,
                                                     HttpServletRequest request) {
        String email = (req == null) ? null : req.getEmail();
        String password = (req == null) ? null : req.getPassword();

        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email es requerido"));
        }
        if (password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "password es requerido"));
        }
        String emailNorm = email.trim();

        AppUser appUser = appUserRepository
                .findByEmailIgnoreCase(emailNorm)
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .filter(this::hasUserRole)
                .orElse(null);

        if (appUser == null
                || appUser.getPasswordHash() == null
                || appUser.getPasswordHash().isBlank()
                || !passwordEncoder.matches(password, appUser.getPasswordHash())) {
            return unauthorized("Correo o clave inválidos");
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
     * Consulta el estado de verificación del proof. Si el proof se completa y se verifica,
     * autentica al usuario y persiste el {@link SecurityContext} en la sesión.
     *
     * @param presExId identificador del intercambio de presentación
     * @param request request HTTP para acceso a sesión
     * @param response response HTTP (no se modifica, se mantiene para compatibilidad de firma)
     * @return mapa con estado y, si se autentica, URL de redirección y nombre de visualización
     */
    @GetMapping("/poll")
    public ResponseEntity<Map<String, Object>> poll(@RequestParam("presExId") String presExId,
                                                    HttpServletRequest request,
                                                    HttpServletResponse response) {

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
     * Valida el OTP enviado por correo y completa la autenticación de la sesión.
     *
     * @param req request con presExId y código OTP
     * @param request request HTTP para acceso a sesión
     * @return respuesta con estado de autenticación y redirección
     */
    @PostMapping("/otp/verify")
    public ResponseEntity<Map<String, Object>> verifyOtp(@RequestBody OtpVerifyRequest req,
                                                         HttpServletRequest request) {
        String presExId = req == null ? null : normalize(req.getPresExId());
        String code = req == null ? null : normalize(req.getCode());

        if (presExId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "presExId es requerido"));
        }
        if (code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "code es requerido"));
        }

        PendingOtpContext pending = getPendingOtpContext(request, presExId);
        if (pending == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .cacheControl(CacheControl.noStore())
                    .body(Map.of("error", "La sesión de validación expiró. Intenta iniciar sesión nuevamente."));
        }

        boolean ok;
        if (isTotpFactor(pending)) {
            AppUser loginUser = pending.getPersonId() != null
                    ? findActiveUserByPersonId(pending.getPersonId())
                    : findActiveUserByEmail(pending.getLoginEmail());
            ok = userTotpService.verifyLoginCodeAndMark(loginUser, code);
        } else {
            ok = userLoginOtpService.verifyCode(presExId, code);
        }
        if (!ok) {
            int failed = Math.max(0, pending.getFailedOtpAttempts()) + 1;
            pending.setFailedOtpAttempts(failed);

            int remaining = MAX_OTP_LOGIN_ATTEMPTS - failed;
            if (remaining <= 0) {
                removeExpectedIdNumber(request, presExId);
                removeExpectedEmail(request, presExId);
                removePendingOtpContext(request, presExId);
                userLoginOtpService.invalidate(presExId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .cacheControl(CacheControl.noStore())
                        .body(Map.of(
                                "error", "Se agotaron los intentos de verificación. Debes iniciar sesión nuevamente.",
                                "restartLogin", true
                        ));
            }

            savePendingOtpContext(request, presExId, pending);
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

        removeExpectedIdNumber(request, presExId);
        removeExpectedEmail(request, presExId);
        removePendingOtpContext(request, presExId);
        userLoginOtpService.invalidate(presExId);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of(
                        "authenticated", true,
                        "redirectUrl", "/user/dashboard",
                        "displayName", principal.getDisplayName()
                ));
    }

    /**
     * Reenvía el OTP por correo para un flujo de login ya verificado por credencial Indy.
     *
     * @param req request con presExId del flujo en curso
     * @param request request HTTP para acceder al contexto pendiente en sesión
     * @return estado de reenvío y mensaje amigable
     */
    @PostMapping("/otp/resend")
    public ResponseEntity<Map<String, Object>> resendOtp(@RequestBody OtpResendRequest req,
                                                          HttpServletRequest request) {
        String presExId = req == null ? null : normalize(req.getPresExId());
        if (presExId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "presExId es requerido"));
        }

        PendingOtpContext pending = getPendingOtpContext(request, presExId);
        if (pending == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .cacheControl(CacheControl.noStore())
                    .body(Map.of("error", "La sesión de validación expiró. Inicia sesión nuevamente."));
        }
        if (isTotpFactor(pending)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .cacheControl(CacheControl.noStore())
                    .body(Map.of("error", "Este usuario usa app autenticadora. No aplica reenvío por correo."));
        }

        boolean sent = userLoginOtpService.issueCode(
                presExId,
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

    private AppUser findActiveUserByPersonId(Long personId) {
        if (personId == null) {
            return null;
        }
        return appUserRepository.findById(personId)
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
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
                .map(UserAuthController::normalize)
                .orElse("");
    }

    private ResponseEntity<Map<String, Object>> unauthorized(String message) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .cacheControl(CacheControl.noStore())
                .body(Map.of("error", message));
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
}
