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
     * Portada pública principal del proyecto.
     *
     * <p>
     * Presenta el propósito de CCDigital, los módulos disponibles y accesos directos a login/registro.
     * Se publica en la raíz ({@code /}) como carta de presentación del sitio.
     * </p>
     *
     * @return vista principal pública
     */
    @GetMapping("/")
    public String landing() {
        return "index";
    }

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
     * Redirección amigable para accesos GET a la URL de logout de Admin.
     *
     * <p>
     * El cierre de sesión real se realiza por POST vía Spring Security. Este endpoint evita
     * que un acceso manual por URL termine en 404/405 y redirige a la pantalla de login.
     * </p>
     *
     * @return redirección a login de Admin
     */
    @GetMapping("/admin/logout")
    public String adminLogoutGet() {
        return "redirect:/login/admin?logout=true";
    }

    /**
     * Redirección amigable para accesos GET a la URL de logout de Issuer.
     *
     * @return redirección a login de Issuer
     */
    @GetMapping("/issuer/logout")
    public String issuerLogoutGet() {
        return "redirect:/login/issuer?logout=true";
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

    /**
     * Redirección amigable para accesos GET a la URL de logout de usuario final.
     *
     * @return redirección a login de usuario
     */
    @GetMapping("/user/logout")
    public String userLogoutGet() {
        return "redirect:/login/user?logout=true";
    }
}
