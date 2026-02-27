package co.edu.unbosque.ccdigital.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio de segundo factor por correo para el login de usuario final.
 *
 * <p>Genera un código temporal (OTP) asociado a un flujo de autenticación, lo envía por correo y
 * valida el código con expiración y límite de intentos. El store es en memoria (suficiente para
 * desarrollo/entorno simple), por lo que los códigos se pierden si la aplicación reinicia.</p>
 */
@Service
public class UserLoginOtpService {

    private static final Logger log = LoggerFactory.getLogger(UserLoginOtpService.class);
    private static final SecureRandom RNG = new SecureRandom();

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    private static final class LoginOtpChallenge {
        private final String codeHash;
        // Timestamp de creación para aplicar ventana anti-reenvío inmediato.
        private final Instant issuedAt;
        private final Instant expiresAt;
        private int failedAttempts;

        private LoginOtpChallenge(String codeHash, Instant issuedAt, Instant expiresAt) {
            this.codeHash = codeHash;
            this.issuedAt = issuedAt;
            this.expiresAt = expiresAt;
        }
    }

    private final Map<String, LoginOtpChallenge> challenges = new ConcurrentHashMap<>();

    @Value("${app.security.login-otp.code-length:6}")
    private int codeLength;

    @Value("${app.security.login-otp.code-ttl-minutes:5}")
    private long codeTtlMinutes;

    @Value("${app.security.login-otp.max-attempts:5}")
    private int maxAttempts;

    /**
     * Tiempo mínimo entre envíos consecutivos para un mismo flowId.
     */
    @Value("${app.security.login-otp.resend-cooldown-seconds:45}")
    private long resendCooldownSeconds;

    @Value("${app.security.login-otp.mail.from:}")
    private String mailFrom;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    public UserLoginOtpService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    /**
     * Emite y envía un código OTP para un flujo de login.
     *
     * @param flowId identificador del flujo (se recomienda usar presExId)
     * @param email correo destino
     * @param displayName nombre visible del usuario (opcional)
     * @return {@code true} si se pudo enviar; {@code false} si falló SMTP/configuración
     */
    public synchronized boolean issueCode(String flowId, String email, String displayName) {
        if (isBlank(flowId) || isBlank(email)) {
            return false;
        }

        prune(flowId);
        LoginOtpChallenge existing = challenges.get(flowId.trim());
        if (existing != null
                && Duration.between(existing.issuedAt, Instant.now()).getSeconds() < Math.max(1, resendCooldownSeconds)) {
            // No genera código nuevo dentro de la ventana: evita spam y abuso del endpoint.
            return true;
        }

        String code = generateNumericCode(safeCodeLength());
        LoginOtpChallenge challenge = new LoginOtpChallenge(
                sha256Base64(code),
                Instant.now(),
                Instant.now().plus(Duration.ofMinutes(Math.max(1, codeTtlMinutes)))
        );

        if (!sendEmail(email.trim(), displayName, code)) {
            return false;
        }

        challenges.put(flowId.trim(), challenge);
        return true;
    }

    /**
     * Valida el código OTP para el flujo indicado.
     *
     * @param flowId identificador del flujo
     * @param code código en texto plano
     * @return {@code true} si el código es válido; {@code false} si es inválido/expirado
     */
    public synchronized boolean verifyCode(String flowId, String code) {
        if (isBlank(flowId) || isBlank(code)) return false;

        String key = flowId.trim();
        prune(key);

        LoginOtpChallenge c = challenges.get(key);
        if (c == null) return false;

        if (c.failedAttempts >= Math.max(1, maxAttempts)) {
            challenges.remove(key);
            return false;
        }

        boolean ok = MessageDigest.isEqual(
                c.codeHash.getBytes(StandardCharsets.UTF_8),
                sha256Base64(code.trim()).getBytes(StandardCharsets.UTF_8)
        );

        if (!ok) {
            c.failedAttempts++;
            if (c.failedAttempts >= Math.max(1, maxAttempts)) {
                challenges.remove(key);
            }
            return false;
        }

        challenges.remove(key);
        return true;
    }

    /**
     * Elimina explícitamente un OTP pendiente de un flujo de login.
     *
     * @param flowId identificador del flujo
     */
    public synchronized void invalidate(String flowId) {
        if (!isBlank(flowId)) {
            challenges.remove(flowId.trim());
        }
    }

    private void prune(String flowId) {
        LoginOtpChallenge c = challenges.get(flowId);
        if (c != null && Instant.now().isAfter(c.expiresAt)) {
            challenges.remove(flowId);
        }
    }

    private boolean sendEmail(String to, String displayName, String code) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("Login OTP: JavaMailSender no está configurado.");
            return false;
        }

        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            String from = !isBlank(mailFrom) ? mailFrom.trim() : (!isBlank(smtpUsername) ? smtpUsername.trim() : null);
            if (!isBlank(from)) {
                msg.setFrom(from);
            }
            msg.setTo(to);
            msg.setSubject("CCDigital - Código de verificación de ingreso");
            msg.setText(buildBody(displayName, code));
            sender.send(msg);
            return true;
        } catch (Exception ex) {
            log.error("No se pudo enviar OTP de login a {}", to, ex);
            return false;
        }
    }

    private String buildBody(String displayName, String code) {
        String name = isBlank(displayName) ? "usuario" : displayName.trim();
        long ttl = Math.max(1, codeTtlMinutes);
        return "Hola " + name + ",\n\n"
                + "Tu código de verificación para ingresar a CCDigital es: " + code + "\n\n"
                + "Este código vence en " + ttl + " minutos y solo puede usarse una vez.\n"
                + "Si no intentaste iniciar sesión, ignora este correo.\n";
    }

    private String generateNumericCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RNG.nextInt(10));
        }
        return sb.toString();
    }

    private int safeCodeLength() {
        return Math.max(4, Math.min(8, codeLength));
    }

    private String sha256Base64(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar hash del OTP de login", e);
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
