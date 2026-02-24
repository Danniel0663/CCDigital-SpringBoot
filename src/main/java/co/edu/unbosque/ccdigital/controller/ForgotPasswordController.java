package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.dto.ForgotResetRequest;
import co.edu.unbosque.ccdigital.dto.ForgotVerifyRequest;
import co.edu.unbosque.ccdigital.service.ForgotPasswordService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * API REST del flujo de recuperación de contraseña para usuarios finales.
 *
 * <p>Flujo implementado:</p>
 * <ol>
 *   <li>{@code /verify}: valida datos básicos (correo + documento) y solicita envío de código por correo.</li>
 *   <li>{@code /reset}: valida el código temporal y actualiza la contraseña con BCrypt.</li>
 * </ol>
 *
 * <p>Las respuestas del endpoint {@code /verify} son intencionalmente genéricas para no exponer
 * si un correo/documento existe en el sistema (mitigación de enumeración de usuarios).</p>
 */
@RestController
@RequestMapping("/user/auth/forgot")
public class ForgotPasswordController {

    private final ForgotPasswordService forgotPasswordService;

    /**
     * Constructor con inyección del servicio de recuperación segura.
     *
     * @param forgotPasswordService servicio que administra envío de código, validación y reset
     */
    public ForgotPasswordController(ForgotPasswordService forgotPasswordService) {
        this.forgotPasswordService = forgotPasswordService;
    }

    /**
     * Inicia la recuperación de contraseña enviando un código temporal al correo del usuario.
     *
     * <p>La respuesta es genérica para evitar revelar existencia de usuarios, excepto cuando ocurre
     * un error operativo real (por ejemplo, fallo SMTP) y no es posible intentar el envío.</p>
     *
     * @param req payload con correo + tipo/número de identificación
     * @return JSON con:
     *         <ul>
     *           <li>{@code accepted}: si se pudo procesar la solicitud</li>
     *           <li>{@code message}: mensaje amigable para UI</li>
     *         </ul>
     */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(@RequestBody ForgotVerifyRequest req) {
        boolean accepted = forgotPasswordService.verify(req.getEmail(), req.getIdType(), req.getIdNumber());
        if (!accepted) {
            return ResponseEntity.ok(Map.of(
                    "accepted", false,
                    "message", "No fue posible enviar el código de recuperación. Intente nuevamente."
            ));
        }

        // Respuesta genérica para no exponer si el correo/documento existe o no.
        return ResponseEntity.ok(Map.of(
                "accepted", true,
                "message", "Si los datos coinciden, se envió un código temporal al correo registrado."
        ));
    }

    /**
     * Completa la recuperación de contraseña validando el código temporal enviado por correo.
     *
     * @param req payload con correo, documento, código temporal ({@code resetCode}) y nueva contraseña
     * @return JSON con:
     *         <ul>
     *           <li>{@code ok}: si la contraseña fue actualizada</li>
     *           <li>{@code message}: mensaje amigable para la UI</li>
     *         </ul>
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> reset(@RequestBody ForgotResetRequest req) {
        boolean ok = forgotPasswordService.reset(
                req.getEmail(),
                req.getIdType(),
                req.getIdNumber(),
                req.getResetCode(),
                req.getNewPassword()
        );
        return ResponseEntity.ok(Map.of(
                "ok", ok,
                "message", ok
                        ? "Contraseña actualizada correctamente."
                        : "No fue posible actualizar la contraseña. Verifica el código o solicita uno nuevo."
        ));
    }
}
