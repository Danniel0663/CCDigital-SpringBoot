package co.edu.unbosque.ccdigital.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controlador web para vistas de autenticación.
 *
 * <p>
 * Centraliza el acceso a las pantallas de login por tipo de usuario:
 * Admin, Issuer y User.
 * </p>
 *
 * @since 3.0
 */
@Controller
public class AuthController {

    /**
     * Vista raíz de autenticación.
     *
     * @return nombre de la vista de login para usuario final
     */
    @GetMapping("/login")
    public String loginRoot() {
        return "auth/login-user";
    }

    /**
     * Vista de autenticación para el módulo administrativo.
     *
     * @return nombre de la vista de login de Admin
     */
    @GetMapping("/login/admin")
    public String loginAdmin() {
        return "auth/login-admin";
    }

    /**
     * Vista de autenticación para emisores (Issuer).
     *
     * @return nombre de la vista de login de Issuer
     */
    @GetMapping("/login/issuer")
    public String loginIssuer() {
        return "auth/login-issuer";
    }

    /**
     * Vista de autenticación para usuario final.
     *
     * <p>
     * Si ya existe una sesión autenticada con rol {@code ROLE_USER}, redirige al dashboard.
     * </p>
     *
     * @param authentication autenticación actual (puede ser {@code null})
     * @return vista de login o redirección al dashboard
     */
    @GetMapping("/login/user")
    public String loginUser(Authentication authentication) {

        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_USER".equals(a.getAuthority()))) {
            return "redirect:/user/dashboard";
        }

        return "auth/login-user";
    }
}
