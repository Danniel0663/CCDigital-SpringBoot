package co.edu.unbosque.ccdigital.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filtro simple de rate limit para endpoints sensibles (login, OTP, recuperación y registro).
 *
 * <p>Objetivo: reducir intentos masivos por URL sin introducir dependencias externas.
 * Implementa una ventana deslizante en memoria por clave {@code IP + endpoint}.</p>
 *
 * <p>Limitaciones: al reiniciar la app se pierde el estado y no se comparte entre instancias.</p>
 */
@Component
public class SensitiveEndpointRateLimitFilter extends OncePerRequestFilter {

    /**
     * Endpoints POST considerados sensibles frente a fuerza bruta/abuso.
     *
     * <p>La clave de rate limit se calcula como {@code IP + path}, de modo que cada ruta se
     * limita por separado.</p>
     */
    private static final Set<String> SENSITIVE_POST_PATHS = Set.of(
            "/login/admin",
            "/login/issuer",
            "/user/auth/start",
            "/user/auth/otp/verify",
            "/user/auth/otp/resend",
            "/user/auth/forgot/verify",
            "/user/auth/forgot/reset",
            "/register/user",
            "/register/user/email-otp/resend",
            "/register/user/totp/confirm"
    );

    private final Map<String, ArrayDeque<Long>> hitsByKey = new ConcurrentHashMap<>();

    @Value("${app.security.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${app.security.rate-limit.window-seconds:60}")
    private long windowSeconds;

    @Value("${app.security.rate-limit.max-requests-per-window:20}")
    private int maxRequestsPerWindow;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (!enabled || request == null) return true;
        String method = request.getMethod();
        String path = request.getRequestURI();
        return method == null
                || path == null
                || !"POST".equalsIgnoreCase(method)
                || !SENSITIVE_POST_PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String clientKey = clientIp(request) + "|" + path;

        long nowMs = Instant.now().toEpochMilli();
        long windowMs = Math.max(10, windowSeconds) * 1000L;
        int maxHits = Math.max(3, maxRequestsPerWindow);

        // Cola de timestamps por cliente+ruta para implementar ventana deslizante.
        ArrayDeque<Long> deque = hitsByKey.computeIfAbsent(clientKey, k -> new ArrayDeque<>());
        boolean blocked;
        synchronized (deque) {
            while (!deque.isEmpty() && (nowMs - deque.peekFirst()) > windowMs) {
                deque.pollFirst();
            }
            blocked = deque.size() >= maxHits;
            if (!blocked) {
                deque.addLast(nowMs);
            }
        }

        if (blocked) {
            response.setStatus(429);
            response.setCharacterEncoding("UTF-8");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Demasiados intentos. Intente nuevamente en unos segundos.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Resuelve la IP del cliente considerando cabeceras comunes de proxy reverso.
     *
     * @param request request HTTP actual
     * @return IP normalizada usada para rate limit
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String remote = request.getRemoteAddr();
        return (remote == null || remote.isBlank()) ? "unknown" : remote.trim();
    }
}
