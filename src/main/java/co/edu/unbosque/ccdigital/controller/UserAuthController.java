package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.security.IndyUserPrincipal;
import co.edu.unbosque.ccdigital.service.IndyProofLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
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

    private final IndyProofLoginService proofLoginService;

    /**
     * Constructor del controlador.
     *
     * @param proofLoginService servicio encargado del flujo de proof/login
     */
    public UserAuthController(IndyProofLoginService proofLoginService) {
        this.proofLoginService = proofLoginService;
    }

    /**
     * Request para iniciar el flujo de autenticación por proof.
     *
     * @since 3.0
     */
    public static class StartRequest {

        private String idNumber;

        /**
         * Número de identificación usado para iniciar el login por proof.
         *
         * @return número de identificación
         */
        public String getIdNumber() {
            return idNumber;
        }

        /**
         * Establece el número de identificación para iniciar el flujo.
         *
         * @param idNumber número de identificación
         */
        public void setIdNumber(String idNumber) {
            this.idNumber = idNumber;
        }
    }

    /**
     * Inicia el flujo de autenticación por proof para un número de identificación.
     *
     * @param req request con el número de identificación
     * @return respuesta con el identificador de intercambio de presentación (presExId)
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start(@RequestBody StartRequest req) {
        String idNumber = (req == null) ? null : req.getIdNumber();
        if (idNumber == null || idNumber.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "idNumber es requerido"));
        }

        Map<String, Object> startResp = proofLoginService.startLoginByIdNumber(idNumber.trim());

        Object presExId = startResp.get("presExId");
        if (presExId == null) presExId = startResp.get("pres_ex_id");
        if (presExId == null) presExId = startResp.get("presentation_exchange_id");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("presExId", presExId != null ? String.valueOf(presExId) : null);

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

        Map<String, Object> status = proofLoginService.getProofStatus(presExId.trim());

        boolean done = Boolean.TRUE.equals(status.get("done"));
        boolean verified = Boolean.TRUE.equals(status.get("verified"));

        Map<String, Object> out = new LinkedHashMap<>(status);

        if (done && verified) {
            Map<String, String> attrs = proofLoginService.getVerifiedResultWithAttrs(presExId.trim());

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
        } else {
            out.put("authenticated", false);
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(out);
    }
}
