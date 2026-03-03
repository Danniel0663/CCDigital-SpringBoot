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
 * Servicio OTP por correo para verificación previa al registro de usuario final.
 *
 * <p>Se usa para confirmar que el correo ingresado es accesible antes de crear la cuenta en
 * {@code users}. El almacenamiento es en memoria y los códigos expiran automáticamente.</p>
 */
@Service
public class UserRegisterEmailOtpService {

    private static final Logger log = LoggerFactory.getLogger(UserRegisterEmailOtpService.class);
    private static final SecureRandom RNG = new SecureRandom();

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    private static final class Challenge {
        private final String codeHash;
        // Timestamp de creación para controlar reenvío inmediato del mismo flujo.
        private final Instant issuedAt;
        private final Instant expiresAt;
        private int failedAttempts;

        private Challenge(String codeHash, Instant issuedAt, Instant expiresAt) {
            this.codeHash = codeHash;
            this.issuedAt = issuedAt;
            this.expiresAt = expiresAt;
        }
    }

    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();

    @Value("${app.security.register-email-otp.code-length:6}")
    private int codeLength;

    @Value("${app.security.register-email-otp.code-ttl-minutes:10}")
    private long codeTtlMinutes;

    @Value("${app.security.register-email-otp.max-attempts:5}")
    private int maxAttempts;

    /**
     * Ventana mínima entre reenvíos de OTP para el mismo token de registro.
     */
    @Value("${app.security.register-email-otp.resend-cooldown-seconds:45}")
    private long resendCooldownSeconds;

    @Value("${app.security.register-email-otp.mail.from:}")
    private String mailFrom;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    /**
     * Constructor del servicio.
     *
     * @param mailSenderProvider proveedor lazy de {@link JavaMailSender}
     */
    public UserRegisterEmailOtpService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    /**
     * Genera y envía un código de verificación de correo para el flujo de registro.
     *
     * @param flowId identificador del flujo de registro (token temporal)
     * @param email correo destino
     * @param displayName nombre del ciudadano (opcional)
     * @return {@code true} si el código fue enviado
     */
    public synchronized boolean issueCode(String flowId, String email, String displayName) {
        if (isBlank(flowId) || isBlank(email)) return false;

        String key = flowId.trim();
        prune(key);
        Challenge existing = challenges.get(key);
        if (existing != null
                && Duration.between(existing.issuedAt, Instant.now()).getSeconds() < Math.max(1, resendCooldownSeconds)) {
            // Dentro de la ventana no se emite un nuevo código para evitar spam de correo.
            return true;
        }

        String code = generateNumericCode(safeCodeLength());
        Challenge c = new Challenge(
                sha256Base64(code),
                Instant.now(),
                Instant.now().plus(Duration.ofMinutes(Math.max(1, codeTtlMinutes)))
        );

        if (!sendEmail(email.trim(), displayName, code)) {
            return false;
        }

        challenges.put(key, c);
        return true;
    }

    /**
     * Valida el código del flujo de verificación de correo.
     *
     * @param flowId identificador del flujo
     * @param code código ingresado
     * @return {@code true} si es válido y vigente
     */
    public synchronized boolean verifyCode(String flowId, String code) {
        if (isBlank(flowId) || isBlank(code)) return false;

        String key = flowId.trim();
        prune(key);
        Challenge c = challenges.get(key);
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
     * Invalida manualmente un código pendiente del flujo de registro.
     *
     * @param flowId identificador del flujo
     */
    public synchronized void invalidate(String flowId) {
        if (!isBlank(flowId)) {
            challenges.remove(flowId.trim());
        }
    }

    /**
     * Elimina un desafío expirado del mapa en memoria si corresponde.
     *
     * @param flowId identificador del flujo de verificación de correo
     */
    private void prune(String flowId) {
        Challenge c = challenges.get(flowId);
        if (c != null && Instant.now().isAfter(c.expiresAt)) {
            challenges.remove(flowId);
        }
    }

    /**
     * Envía el correo con el código OTP de verificación de registro.
     *
     * @param to correo destino
     * @param displayName nombre visible del ciudadano (opcional)
     * @param code código OTP generado
     * @return {@code true} si el envío fue exitoso
     */
    private boolean sendEmail(String to, String displayName, String code) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("Register email OTP: JavaMailSender no está configurado.");
            return false;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            String from = !isBlank(mailFrom) ? mailFrom.trim() : (!isBlank(smtpUsername) ? smtpUsername.trim() : null);
            if (!isBlank(from)) {
                msg.setFrom(from);
            }
            msg.setTo(to);
            msg.setSubject("CCDigital - Verificación de correo para registro");
            msg.setText(buildBody(displayName, code));
            sender.send(msg);
            return true;
        } catch (Exception ex) {
            log.error("No se pudo enviar OTP de verificación de registro a {}", to, ex);
            return false;
        }
    }

    /**
     * Construye el cuerpo del correo de verificación de registro.
     *
     * @param displayName nombre visible del ciudadano
     * @param code código OTP
     * @return contenido de texto plano del correo
     */
    private String buildBody(String displayName, String code) {
        String name = isBlank(displayName) ? "usuario" : displayName.trim();
        long ttl = Math.max(1, codeTtlMinutes);
        return "Hola " + name + ",\n\n"
                + "Tu código para verificar el correo y completar el registro en CCDigital es: " + code + "\n\n"
                + "Este código vence en " + ttl + " minutos y solo puede usarse una vez.\n"
                + "Si no solicitaste este registro, ignora este correo.\n";
    }

    /**
     * Genera un código numérico aleatorio para OTP.
     *
     * @param length longitud deseada
     * @return código OTP solo numérico
     */
    private String generateNumericCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RNG.nextInt(10));
        }
        return sb.toString();
    }

    /**
     * Normaliza la longitud del OTP a un rango seguro soportado por la UI.
     *
     * @return longitud efectiva del código
     */
    private int safeCodeLength() {
        return Math.max(4, Math.min(8, codeLength));
    }

    /**
     * Calcula hash SHA-256 (Base64) del código para no almacenarlo en texto plano.
     *
     * @param value valor a resumir
     * @return hash Base64
     */
    private String sha256Base64(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar hash del OTP de verificación de registro", e);
        }
    }

    /**
     * Verifica si una cadena está vacía o contiene solo espacios.
     *
     * @param s valor a evaluar
     * @return {@code true} si es nula/blanca
     */
    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
