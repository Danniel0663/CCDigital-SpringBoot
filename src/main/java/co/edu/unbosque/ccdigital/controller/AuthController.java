package co.edu.unbosque.ccdigital.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controlador MVC para vistas de autenticación.
 *
 * @since 1.0.0
 */
@Controller
public class AuthController {

    /**
     * Retorna la vista de inicio de sesión para usuarios administrativos.
     *
     * @return vista de login administrativo
     */
    @GetMapping("/login/admin")
    public String loginAdmin() {
        return "auth/login-admin";
    }

    /**
     * Retorna la vista de inicio de sesión para emisores.
     *
     * @return vista de login de emisor
     */
    @GetMapping("/login/issuer")
    public String loginIssuer() {
        return "auth/login-issuer";
    }
}
