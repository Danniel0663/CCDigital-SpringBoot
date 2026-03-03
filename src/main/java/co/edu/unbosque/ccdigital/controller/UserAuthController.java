package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.service.UserAuthFlowService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controlador REST para autenticación de usuario final.
 *
 * <p>Este controlador actúa como capa HTTP delgada y delega la lógica de negocio
 * al servicio de aplicación {@link UserAuthFlowService}.</p>
 */
@RestController
@RequestMapping("/user/auth")
public class UserAuthController {

    private final UserAuthFlowService authFlowService;

    public UserAuthController(UserAuthFlowService authFlowService) {
        this.authFlowService = authFlowService;
    }

    /**
     * Request para iniciar el flujo de autenticación por proof.
     */
    public static class StartRequest {
        private String email;
        private String password;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    /**
     * Request para validar segundo factor durante login.
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
     * Request para reenviar/activar OTP por correo durante login.
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
     * Inicia autenticación por credencial verificable.
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start(@RequestBody StartRequest req,
                                                     HttpServletRequest request) {
        return authFlowService.start(
                req == null ? null : req.getEmail(),
                req == null ? null : req.getPassword(),
                request
        );
    }

    /**
     * Consulta estado del intercambio de verificación.
     */
    @GetMapping("/poll")
    public ResponseEntity<Map<String, Object>> poll(@RequestParam("presExId") String presExId,
                                                    HttpServletRequest request,
                                                    HttpServletResponse response) {
        return authFlowService.poll(presExId, request);
    }

    /**
     * Verifica código de segundo factor.
     */
    @PostMapping("/otp/verify")
    public ResponseEntity<Map<String, Object>> verifyOtp(@RequestBody OtpVerifyRequest req,
                                                         HttpServletRequest request) {
        return authFlowService.verifyOtp(
                req == null ? null : req.getPresExId(),
                req == null ? null : req.getCode(),
                request
        );
    }

    /**
     * Reenvía OTP por correo o activa fallback desde app autenticadora.
     */
    @PostMapping("/otp/resend")
    public ResponseEntity<Map<String, Object>> resendOtp(@RequestBody OtpResendRequest req,
                                                          HttpServletRequest request) {
        return authFlowService.resendOtp(
                req == null ? null : req.getPresExId(),
                request
        );
    }
}
