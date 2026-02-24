package co.edu.unbosque.ccdigital.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * Servicio TOTP (RFC 6238) para MFA con aplicaciones autenticadoras móviles.
 *
 * <p>Genera secretos Base32, construye la URI {@code otpauth://} y valida códigos de 6 dígitos
 * usando HMAC-SHA1 compatible con Google Authenticator, Aegis, 2FAS y Microsoft Authenticator.</p>
 */
@Service
public class TotpService {

    private static final SecureRandom RNG = new SecureRandom();
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    @Value("${app.security.totp.issuer-name:CCDigital}")
    private String issuerName;

    @Value("${app.security.totp.digits:6}")
    private int digits;

    @Value("${app.security.totp.period-seconds:30}")
    private long periodSeconds;

    @Value("${app.security.totp.window-steps:1}")
    private int windowSteps;

    /**
     * Genera un secreto Base32 para TOTP.
     *
     * @return secreto Base32 (sin padding), típicamente 32 chars aprox.
     */
    public String generateSecretBase32() {
        byte[] random = new byte[20]; // 160 bits
        RNG.nextBytes(random);
        return base32Encode(random);
    }

    /**
     * Construye la URI otpauth que usan las apps autenticadoras para registrar la cuenta.
     *
     * @param accountLabel etiqueta visible (ej. correo o "CC:12345")
     * @param secretBase32 secreto TOTP en Base32
     * @return URI otpauth compatible con apps TOTP
     */
    public String buildOtpauthUri(String accountLabel, String secretBase32) {
        String issuer = safe(issuerName);
        String label = urlEncode(issuer + ":" + safe(accountLabel));
        return "otpauth://totp/" + label
                + "?secret=" + urlEncode(safe(secretBase32))
                + "&issuer=" + urlEncode(issuer)
                + "&algorithm=SHA1"
                + "&digits=" + safeDigits()
                + "&period=" + safePeriod();
    }

    /**
     * Valida un código TOTP en la ventana de tiempo configurada.
     *
     * @param secretBase32 secreto Base32
     * @param code código de 6 dígitos
     * @return resultado con validez y time-step usado
     */
    public ValidationResult validate(String secretBase32, String code) {
        if (isBlank(secretBase32) || isBlank(code)) return ValidationResult.invalid();

        String normalizedCode = code.trim();
        if (!normalizedCode.matches("\\d{" + safeDigits() + "}")) return ValidationResult.invalid();

        byte[] secret;
        try {
            secret = base32Decode(secretBase32);
        } catch (Exception e) {
            return ValidationResult.invalid();
        }

        long currentStep = timeStepNow();
        for (long step = currentStep - safeWindow(); step <= currentStep + safeWindow(); step++) {
            String expected = hotp(secret, step, safeDigits());
            if (expected.equals(normalizedCode)) {
                return ValidationResult.valid(step);
            }
        }
        return ValidationResult.invalid();
    }

    /**
     * @return time-step actual usado por TOTP
     */
    public long timeStepNow() {
        return Instant.now().getEpochSecond() / safePeriod();
    }

    public int getDigits() {
        return safeDigits();
    }

    public long getPeriodSeconds() {
        return safePeriod();
    }

    private String hotp(byte[] secret, long counter, int digits) {
        try {
            byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hmac = mac.doFinal(counterBytes);

            int offset = hmac[hmac.length - 1] & 0x0F;
            int binary = ((hmac[offset] & 0x7F) << 24)
                    | ((hmac[offset + 1] & 0xFF) << 16)
                    | ((hmac[offset + 2] & 0xFF) << 8)
                    | (hmac[offset + 3] & 0xFF);

            int mod = (int) Math.pow(10, digits);
            int otp = binary % mod;
            return String.format("%0" + digits + "d", otp);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo calcular TOTP", e);
        }
    }

    private String base32Encode(byte[] data) {
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                out.append(BASE32[(buffer >> (bitsLeft - 5)) & 0x1F]);
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            out.append(BASE32[(buffer << (5 - bitsLeft)) & 0x1F]);
        }
        return out.toString();
    }

    private byte[] base32Decode(String input) {
        String s = input == null ? "" : input.trim().replace("=", "").replace(" ", "").toUpperCase();
        if (s.isEmpty()) return new byte[0];

        int buffer = 0;
        int bitsLeft = 0;
        byte[] out = new byte[(s.length() * 5) / 8 + 1];
        int outPos = 0;
        for (char c : s.toCharArray()) {
            int val = base32Val(c);
            if (val < 0) throw new IllegalArgumentException("Base32 inválido");
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out[outPos++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }

        byte[] exact = new byte[outPos];
        System.arraycopy(out, 0, exact, 0, outPos);
        return exact;
    }

    private int base32Val(char c) {
        if (c >= 'A' && c <= 'Z') return c - 'A';
        if (c >= '2' && c <= '7') return 26 + (c - '2');
        return -1;
    }

    private int safeDigits() {
        return Math.max(6, Math.min(8, digits));
    }

    private long safePeriod() {
        return Math.max(15, Math.min(120, periodSeconds));
    }

    private int safeWindow() {
        return Math.max(0, Math.min(3, windowSteps));
    }

    private String safe(String s) {
        return isBlank(s) ? "CCDigital" : s.trim();
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String urlEncode(String s) {
        // RFC3986 enough for otpauth URI
        StringBuilder out = new StringBuilder();
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) b;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                out.append(c);
            } else {
                out.append('%');
                out.append(String.format("%02X", b));
            }
        }
        return out.toString();
    }

    /**
     * Resultado de validación TOTP incluyendo el time-step aceptado para prevenir reuso.
     */
    public static final class ValidationResult {
        private final boolean valid;
        private final long timeStep;

        private ValidationResult(boolean valid, long timeStep) {
            this.valid = valid;
            this.timeStep = timeStep;
        }

        public static ValidationResult valid(long timeStep) {
            return new ValidationResult(true, timeStep);
        }

        public static ValidationResult invalid() {
            return new ValidationResult(false, -1L);
        }

        public boolean isValid() {
            return valid;
        }

        public long getTimeStep() {
            return timeStep;
        }
    }
}
