package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.dto.UserRegisterForm;
import co.edu.unbosque.ccdigital.service.UserRegistrationFlowService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * Controlador web para registro de cuentas de usuario final.
 *
 * <p>Este controlador conserva los endpoints y delega el flujo de negocio
 * al servicio de aplicación {@link UserRegistrationFlowService}.</p>
 */
@Controller
@RequestMapping("/register/user")
public class UserRegistrationController {

    private final UserRegistrationFlowService registrationFlowService;

    public UserRegistrationController(UserRegistrationFlowService registrationFlowService) {
        this.registrationFlowService = registrationFlowService;
    }

    /**
     * Request AJAX para confirmar TOTP luego del registro.
     */
    public static class RegisterTotpConfirmRequest {
        private Long personId;
        private String code;

        public Long getPersonId() {
            return personId;
        }

        public void setPersonId(Long personId) {
            this.personId = personId;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }
    }

    /**
     * Request AJAX para obtener la configuración TOTP pendiente del registro.
     */
    public static class RegisterTotpSetupRequest {
        private Long personId;

        public Long getPersonId() {
            return personId;
        }

        public void setPersonId(Long personId) {
            this.personId = personId;
        }
    }

    /**
     * Request AJAX para reenviar código de verificación de correo.
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
     * Muestra formulario de registro.
     */
    @GetMapping
    public String form(Model model) {
        registrationFlowService.prepareForm(model);
        return "auth/register-user";
    }

    /**
     * Procesa el flujo web de registro.
     */
    @PostMapping
    public String register(@ModelAttribute("form") UserRegisterForm form,
                           Model model,
                           HttpServletRequest request) {
        return registrationFlowService.register(form, model, request);
    }

    /**
     * Confirma activación TOTP opcional después del registro.
     */
    @PostMapping("/totp/confirm")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> confirmRegisterTotp(@RequestBody RegisterTotpConfirmRequest req,
                                                                   HttpServletRequest request) {
        return registrationFlowService.confirmRegisterTotp(
                req == null ? null : req.getPersonId(),
                req == null ? null : req.getCode(),
                request
        );
    }

    /**
     * Obtiene el secreto/URI TOTP del paso opcional posterior al registro.
     */
    @PostMapping("/totp/setup")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> registerTotpSetup(@RequestBody RegisterTotpSetupRequest req,
                                                                 HttpServletRequest request) {
        return registrationFlowService.getRegisterTotpSetup(
                req == null ? null : req.getPersonId(),
                request
        );
    }

    /**
     * Reenvía OTP de verificación de correo para registro pendiente.
     */
    @PostMapping("/email-otp/resend")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> resendRegisterEmailOtp(@RequestBody RegisterEmailResendRequest req,
                                                                       HttpServletRequest request) {
        return registrationFlowService.resendRegisterEmailOtp(
                req == null ? null : req.getEmailToken(),
                request
        );
    }
}
