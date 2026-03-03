package co.edu.unbosque.ccdigital.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints internos de actividad de sesión para timeout por inactividad en frontend.
 *
 * <p>Las rutas están bajo prefijos protegidos ({@code /user/**}, {@code /issuer/**}, {@code /admin/**}),
 * por lo que solo usuarios autenticados del módulo correspondiente pueden invocarlas.</p>
 */
@RestController
public class SessionActivityController {

    /**
     * Mantiene viva la sesión del usuario final cuando hay actividad real en UI.
     */
    @GetMapping("/user/session/keepalive")
    public ResponseEntity<Void> keepAliveUser() {
        return ResponseEntity.noContent().build();
    }

    /**
     * Mantiene viva la sesión del emisor cuando hay actividad real en UI.
     */
    @GetMapping("/issuer/session/keepalive")
    public ResponseEntity<Void> keepAliveIssuer() {
        return ResponseEntity.noContent().build();
    }

    /**
     * Mantiene viva la sesión del administrador cuando hay actividad real en UI.
     */
    @GetMapping("/admin/session/keepalive")
    public ResponseEntity<Void> keepAliveAdmin() {
        return ResponseEntity.noContent().build();
    }

    /**
     * Expira explícitamente la sesión del usuario final (idle timeout en cliente).
     */
    @GetMapping("/user/session/expire")
    public ResponseEntity<Void> expireUser(HttpServletRequest request) {
        invalidateSession(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Expira explícitamente la sesión del emisor (idle timeout en cliente).
     */
    @GetMapping("/issuer/session/expire")
    public ResponseEntity<Void> expireIssuer(HttpServletRequest request) {
        invalidateSession(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Expira explícitamente la sesión del administrador (idle timeout en cliente).
     */
    @GetMapping("/admin/session/expire")
    public ResponseEntity<Void> expireAdmin(HttpServletRequest request) {
        invalidateSession(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Invalida sesión HTTP y limpia el contexto de seguridad del hilo actual.
     */
    private void invalidateSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}

