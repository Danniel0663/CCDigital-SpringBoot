package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.entity.AppUser;
import co.edu.unbosque.ccdigital.entity.Person;
import co.edu.unbosque.ccdigital.repository.AppUserRepository;
import co.edu.unbosque.ccdigital.repository.PersonRepository;
import co.edu.unbosque.ccdigital.security.IndyUserPrincipal;
import co.edu.unbosque.ccdigital.service.IndyProofLoginService;
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

    private static final String SESSION_EXPECTED_ID_NUMBERS = "user.auth.expectedIdNumbersByPresExId";

    private final IndyProofLoginService proofLoginService;
    private final AppUserRepository appUserRepository;
    private final PersonRepository personRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Constructor del controlador.
     *
     * @param proofLoginService servicio encargado del flujo de proof/login
     * @param appUserRepository repositorio de usuarios de aplicación
     * @param personRepository repositorio de personas
     * @param passwordEncoder comparador de hash de contraseñas (BCrypt)
     */
    public UserAuthController(IndyProofLoginService proofLoginService,
                              AppUserRepository appUserRepository,
                              PersonRepository personRepository,
                              PasswordEncoder passwordEncoder) {
        this.proofLoginService = proofLoginService;
        this.appUserRepository = appUserRepository;
        this.personRepository = personRepository;
        this.passwordEncoder = passwordEncoder;
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

            IndyUserPrincipal principal = new IndyUserPrincipal(
                    attrs.getOrDefault("id_type", ""),
                    attrs.getOrDefault("id_number", ""),
                    attrs.getOrDefault("first_name", ""),
                    attrs.getOrDefault("last_name", ""),
                    attrs.getOrDefault("email", "")
            );

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);

            request.getSession(true).setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    context
            );

            out.put("authenticated", true);
            out.put("redirectUrl", "/user/dashboard");
            out.put("displayName", principal.getDisplayName());
            removeExpectedIdNumber(request, presExIdNorm);
        } else {
            out.put("authenticated", false);
            if (done) {
                removeExpectedIdNumber(request, presExIdNorm);
            }
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(out);
    }

    private boolean hasUserRole(AppUser appUser) {
        String role = normalize(appUser.getRole());
        if (role.startsWith("ROLE_")) {
            role = role.substring("ROLE_".length());
        }
        return "USER".equalsIgnoreCase(role) || "USUARIO".equalsIgnoreCase(role);
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

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
