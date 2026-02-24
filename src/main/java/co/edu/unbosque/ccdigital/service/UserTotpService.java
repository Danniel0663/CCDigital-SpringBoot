package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.entity.AppUser;
import co.edu.unbosque.ccdigital.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Servicio TOTP (RFC 6238) para MFA con apps autenticadoras.
 *
 * <p>Genera secretos Base32, construye URIs {@code otpauth://} compatibles con aplicaciones como
 * Google Authenticator/Aegis y valida códigos con tolerancia de tiempo configurable.</p>
 */
@Service
public class UserTotpService {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RNG = new SecureRandom();

    private final AppUserRepository appUserRepository;

    @Value("${app.security.totp.issuer:CCDigital}")
    private String issuerName;

    @Value("${app.security.totp.secret-bytes:20}")
    private int secretBytes;

    @Value("${app.security.totp.code-digits:6}")
    private int codeDigits;

    @Value("${app.security.totp.period-seconds:30}")
    private long periodSeconds;

    @Value("${app.security.totp.window-steps:1}")
    private int windowSteps;

    public UserTotpService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    /**
     * Resultado de validación TOTP.
     *
     * @param valid indica si el código fue aceptado
     * @param acceptedTimeStep time-step aceptado (para anti reuso)
     */
    public record VerificationResult(boolean valid, long acceptedTimeStep) {}

    /**
     * Genera un nuevo secreto Base32 para enrolar una app autenticadora.
     *
     * @return secreto Base32
     */
    public String generateSecretBase32() {
        int size = Math.max(10, Math.min(64, secretBytes));
        byte[] secret = new byte[size];
        RNG.nextBytes(secret);
        return base32Encode(secret);
    }

    /**
     * Construye una URI {@code otpauth://} compatible con apps TOTP.
     *
     * @param accountLabel etiqueta de la cuenta (por ejemplo correo o identificación)
     * @param secretBase32 secreto TOTP en Base32
     * @return URI para registrar la cuenta en la app
     */
    public String buildOtpAuthUri(String accountLabel, String secretBase32) {
        String issuer = safeIssuer();
        String label = (issuer + ":" + safeAccountLabel(accountLabel));
        return "otpauth://totp/"
                + urlEncode(label)
                + "?secret=" + urlEncode(normalizeSecret(secretBase32))
                + "&issuer=" + urlEncode(issuer)
                + "&algorithm=SHA1"
                + "&digits=" + safeDigits()
                + "&period=" + safePeriodSeconds();
    }

    /**
     * Indica si el usuario tiene MFA TOTP activo y secret válido.
     *
     * @param user usuario a validar
     * @return {@code true} si TOTP está activo
     */
    public boolean isTotpEnabled(AppUser user) {
        return user != null
                && Boolean.TRUE.equals(user.getTotpEnabled())
                && !normalizeSecret(user.getTotpSecretBase32()).isBlank();
    }

    /**
     * Valida un código TOTP frente a un secreto Base32.
     *
     * <p>Aplica tolerancia de reloj configurable (ventana) y evita reusar el mismo código si se
     * provee {@code lastAcceptedTimeStep}.</p>
     *
     * @param secretBase32 secreto TOTP Base32
     * @param code código ingresado por el usuario
     * @param lastAcceptedTimeStep último time-step ya aceptado (anti replay), puede ser null
     * @return resultado de validación
     */
    public VerificationResult verifyCode(String secretBase32, String code, Long lastAcceptedTimeStep) {
        String normalizedCode = normalizeDigits(code);
        if (normalizedCode.length() != safeDigits()) {
            return new VerificationResult(false, -1L);
        }

        byte[] secret = base32Decode(normalizeSecret(secretBase32));
        if (secret.length == 0) {
            return new VerificationResult(false, -1L);
        }

        long stepNow = currentTimeStep();
        int window = Math.max(0, windowSteps);
        for (int offset = -window; offset <= window; offset++) {
            long step = stepNow + offset;
            if (step < 0) {
                continue;
            }
            if (lastAcceptedTimeStep != null && step <= lastAcceptedTimeStep) {
                continue;
            }

            String expected = generateTotpForStep(secret, step);
            if (expected.equals(normalizedCode)) {
                return new VerificationResult(true, step);
            }
        }

        return new VerificationResult(false, -1L);
    }

    /**
     * Valida un TOTP para login y persiste el time-step aceptado del usuario.
     *
     * @param user usuario autenticado localmente (pendiente de MFA)
     * @param code código TOTP ingresado
     * @return {@code true} si el código fue aceptado y se persistió el time-step
     */
    public boolean verifyLoginCodeAndMark(AppUser user, String code) {
        if (!isTotpEnabled(user)) {
            return false;
        }

        VerificationResult result = verifyCode(
                user.getTotpSecretBase32(),
                code,
                user.getTotpLastTimeStep()
        );
        if (!result.valid()) {
            return false;
        }

        user.setTotpLastTimeStep(result.acceptedTimeStep());
        appUserRepository.save(user);
        return true;
    }

    /**
     * Activa TOTP para el usuario persistiendo secreto, marca de tiempo y anti replay.
     *
     * @param user usuario a actualizar
     * @param secretBase32 secreto Base32 confirmado
     * @param acceptedTimeStep time-step del código de confirmación aceptado
     * @return usuario persistido
     */
    public AppUser enableTotp(AppUser user, String secretBase32, long acceptedTimeStep) {
        user.setTotpSecretBase32(normalizeSecret(secretBase32));
        user.setTotpEnabled(Boolean.TRUE);
        user.setTotpConfirmedAt(LocalDateTime.now());
        user.setTotpLastTimeStep(acceptedTimeStep);
        return appUserRepository.save(user);
    }

    /**
     * Desactiva TOTP para el usuario y limpia la configuración almacenada.
     *
     * @param user usuario a actualizar
     * @return usuario persistido
     */
    public AppUser disableTotp(AppUser user) {
        user.setTotpEnabled(Boolean.FALSE);
        user.setTotpSecretBase32(null);
        user.setTotpConfirmedAt(null);
        user.setTotpLastTimeStep(null);
        return appUserRepository.save(user);
    }

    private long currentTimeStep() {
        return Instant.now().getEpochSecond() / safePeriodSeconds();
    }

    private String generateTotpForStep(byte[] secret, long timeStep) {
        byte[] counter = new byte[8];
        long value = timeStep;
        for (int i = 7; i >= 0; i--) {
            counter[i] = (byte) (value & 0xFF);
            value >>>= 8;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hmac = mac.doFinal(counter);
            int offset = hmac[hmac.length - 1] & 0x0F;
            int binary = ((hmac[offset] & 0x7F) << 24)
                    | ((hmac[offset + 1] & 0xFF) << 16)
                    | ((hmac[offset + 2] & 0xFF) << 8)
                    | (hmac[offset + 3] & 0xFF);
            int mod = (int) Math.pow(10, safeDigits());
            int otp = binary % mod;
            return String.format("%0" + safeDigits() + "d", otp);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo generar TOTP", ex);
        }
    }

    private static String base32Encode(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                out.append(BASE32_ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            out.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return out.toString();
    }

    private static byte[] base32Decode(String input) {
        String s = normalizeSecret(input);
        if (s.isBlank()) {
            return new byte[0];
        }

        byte[] out = new byte[(s.length() * 5) / 8];
        int outPos = 0;
        int buffer = 0;
        int bitsLeft = 0;

        for (int i = 0; i < s.length(); i++) {
            int val = BASE32_ALPHABET.indexOf(s.charAt(i));
            if (val < 0) {
                return new byte[0];
            }
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out[outPos++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }

        if (outPos == out.length) {
            return out;
        }
        byte[] trimmed = new byte[outPos];
        System.arraycopy(out, 0, trimmed, 0, outPos);
        return trimmed;
    }

    private int safeDigits() {
        return Math.max(6, Math.min(8, codeDigits));
    }

    private long safePeriodSeconds() {
        return Math.max(15, Math.min(120, periodSeconds));
    }

    private String safeIssuer() {
        String v = (issuerName == null ? "" : issuerName.trim());
        return v.isBlank() ? "CCDigital" : v;
    }

    private static String safeAccountLabel(String label) {
        String v = (label == null ? "" : label.trim());
        return v.isBlank() ? "usuario" : v;
    }

    private static String normalizeSecret(String secret) {
        if (secret == null) return "";
        return secret.replace(" ", "").replace("-", "").trim().toUpperCase();
    }

    private static String normalizeDigits(String code) {
        if (code == null) return "";
        StringBuilder sb = new StringBuilder(code.length());
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c);
        }
        return sb.toString();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
