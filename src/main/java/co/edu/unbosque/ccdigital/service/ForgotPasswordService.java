package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.entity.AppUser;
import co.edu.unbosque.ccdigital.entity.Person;
import co.edu.unbosque.ccdigital.repository.AppUserRepository;
import co.edu.unbosque.ccdigital.repository.PersonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio de recuperación de contraseña para usuarios finales con verificación por correo.
 *
 * <p>Flujo seguro implementado:</p>
 * <ol>
 *   <li>El usuario ingresa correo + tipo/número de documento.</li>
 *   <li>Si coincide con un usuario válido, se envía un código temporal al correo registrado.</li>
 *   <li>El usuario ingresa el código y la nueva contraseña.</li>
 *   <li>El código expira, tiene límite de intentos y se invalida al primer uso exitoso.</li>
 * </ol>
 *
 * <p>No persiste tokens en base de datos para evitar cambios de esquema. Los desafíos se almacenan
 * en memoria con expiración, lo cual es suficiente para una primera capa de seguridad, pero no
 * sobrevive reinicios de la aplicación.</p>
 */
@Service
public class ForgotPasswordService {

    private static final Logger log = LoggerFactory.getLogger(ForgotPasswordService.class);
    private static final SecureRandom RNG = new SecureRandom();

    private final AppUserRepository appUserRepository;
    private final PersonRepository personRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    /**
     * Estado temporal de un código de recuperación emitido.
     */
    private static final class ResetChallenge {
        private final String codeHash;
        private final Instant expiresAt;
        private final Instant issuedAt;
        private int failedAttempts;

        private ResetChallenge(String codeHash, Instant issuedAt, Instant expiresAt) {
            this.codeHash = codeHash;
            this.issuedAt = issuedAt;
            this.expiresAt = expiresAt;
        }
    }

    /**
     * Store en memoria de desafíos de recuperación (clave: email normalizado).
     */
    private final Map<String, ResetChallenge> challengesByEmail = new ConcurrentHashMap<>();

    @Value("${app.security.forgot-password.code-length:6}")
    private int codeLength;

    @Value("${app.security.forgot-password.code-ttl-minutes:10}")
    private long codeTtlMinutes;

    @Value("${app.security.forgot-password.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.security.forgot-password.resend-cooldown-seconds:60}")
    private long resendCooldownSeconds;

    @Value("${app.security.forgot-password.mail.from:no-reply@ccdigital.local}")
    private String mailFrom;

    public ForgotPasswordService(
            AppUserRepository appUserRepository,
            PersonRepository personRepository,
            PasswordEncoder passwordEncoder,
            ObjectProvider<JavaMailSender> mailSenderProvider
    ) {
        this.appUserRepository = appUserRepository;
        this.personRepository = personRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSenderProvider = mailSenderProvider;
    }

    /**
     * Solicita un código de recuperación por correo.
     *
     * <p>Devuelve {@code true} si el proceso se aceptó (respuesta genérica al cliente), incluso cuando
     * la combinación correo/documento no existe, para reducir enumeración de usuarios. Devuelve
     * {@code false} solo en errores operativos (ej. correo no configurado o envío fallido para un caso válido).</p>
     *
     * @param email correo del usuario
     * @param idType tipo de identificación
     * @param idNumber número de identificación
     * @return {@code true} si la solicitud se aceptó; {@code false} si falló el envío/configuración
     */
    public synchronized boolean verify(String email, String idType, String idNumber) {
        String emailNorm = normalizeEmail(email);
        String idTypeNorm = normalizeUpper(idType);
        String idNumNorm = normalize(idNumber);

        if (isBlank(emailNorm) || isBlank(idTypeNorm) || isBlank(idNumNorm)) {
            return true; // respuesta genérica para no filtrar información
        }

        IdentityMatch match = findMatchingActiveUser(emailNorm, idTypeNorm, idNumNorm);
        if (match == null) {
            return true; // no revelar si existe o no
        }

        pruneIfExpired(emailNorm);

        ResetChallenge existing = challengesByEmail.get(emailNorm);
        if (existing != null && Duration.between(existing.issuedAt, Instant.now()).getSeconds() < resendCooldownSeconds) {
            // Ya hay un código vigente recientemente emitido; respuesta genérica sin reenvío.
            return true;
        }

        String code = generateNumericCode(safeCodeLength());
        ResetChallenge challenge = new ResetChallenge(
                sha256Base64(code),
                Instant.now(),
                Instant.now().plus(Duration.ofMinutes(Math.max(1, codeTtlMinutes)))
        );

        if (!sendResetCodeEmail(match.user.getEmail(), match.person.getFullName(), code)) {
            return false;
        }

        challengesByEmail.put(emailNorm, challenge);
        return true;
    }

    /**
     * Aplica el cambio de contraseña validando identidad y código temporal por correo.
     *
     * @param email correo del usuario
     * @param idType tipo de identificación
     * @param idNumber número de identificación
     * @param resetCode código temporal recibido por correo
     * @param newPassword nueva contraseña
     * @return {@code true} si se actualizó correctamente; {@code false} si falló validación/código
     */
    @Transactional
    public synchronized boolean reset(String email, String idType, String idNumber, String resetCode, String newPassword) {
        String emailNorm = normalizeEmail(email);
        String idTypeNorm = normalizeUpper(idType);
        String idNumNorm = normalize(idNumber);
        String codeNorm = normalize(resetCode);
        String passwordNorm = normalize(newPassword);

        if (isBlank(emailNorm) || isBlank(idTypeNorm) || isBlank(idNumNorm) || isBlank(codeNorm)) return false;
        if (!isStrongEnoughPassword(passwordNorm)) return false;

        IdentityMatch match = findMatchingActiveUser(emailNorm, idTypeNorm, idNumNorm);
        if (match == null) return false;

        pruneIfExpired(emailNorm);

        ResetChallenge challenge = challengesByEmail.get(emailNorm);
        if (challenge == null) return false;
        if (challenge.failedAttempts >= Math.max(1, maxAttempts)) {
            challengesByEmail.remove(emailNorm);
            return false;
        }

        boolean codeOk = safeEqualsHash(challenge.codeHash, sha256Base64(codeNorm));
        if (!codeOk) {
            challenge.failedAttempts++;
            if (challenge.failedAttempts >= Math.max(1, maxAttempts)) {
                challengesByEmail.remove(emailNorm);
            }
            return false;
        }

        AppUser matchedUser = Objects.requireNonNull(match.user());
        matchedUser.setPasswordHash(passwordEncoder.encode(passwordNorm));
        appUserRepository.save(matchedUser);

        challengesByEmail.remove(emailNorm);
        return true;
    }

    private boolean sendResetCodeEmail(String to, String fullName, String code) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("Recuperación de contraseña: JavaMailSender no está configurado. Configure spring.mail.*");
            return false;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(to);
            message.setSubject("CCDigital - Código de recuperación de contraseña");
            message.setText(buildMailBody(fullName, code));
            mailSender.send(message);
            return true;
        } catch (Exception ex) {
            log.error("No se pudo enviar correo de recuperación a {}", to, ex);
            return false;
        }
    }

    private String buildMailBody(String fullName, String code) {
        long ttl = Math.max(1, codeTtlMinutes);
        String name = isBlank(fullName) ? "usuario" : fullName;
        return "Hola " + name + ",\n\n"
                + "Se solicitó restablecer tu contraseña en CCDigital.\n"
                + "Tu código temporal es: " + code + "\n\n"
                + "Este código vence en " + ttl + " minutos y solo puede usarse una vez.\n"
                + "Si no realizaste esta solicitud, ignora este correo.\n";
    }

    private IdentityMatch findMatchingActiveUser(String email, String idType, String idNumber) {
        AppUser user = appUserRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) return null;
        if (Boolean.FALSE.equals(user.getIsActive())) return null;

        Long personId = user.getPersonId();
        if (personId == null) return null;

        Person person = personRepository.findById(personId).orElse(null);
        if (person == null) return null;

        String dbType = person.getIdType() == null ? "" : person.getIdType().name();
        String dbNum = normalize(person.getIdNumber());

        if (!dbType.equalsIgnoreCase(idType)) return null;
        if (!dbNum.equalsIgnoreCase(idNumber)) return null;

        return new IdentityMatch(user, person);
    }

    private void pruneIfExpired(String emailNorm) {
        ResetChallenge c = challengesByEmail.get(emailNorm);
        if (c != null && Instant.now().isAfter(c.expiresAt)) {
            challengesByEmail.remove(emailNorm);
        }
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

    private boolean isStrongEnoughPassword(String pwd) {
        if (isBlank(pwd)) return false;
        if (pwd.length() < 8) return false;
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : pwd.toCharArray()) {
            if (Character.isLetter(c)) hasLetter = true;
            if (Character.isDigit(c)) hasDigit = true;
        }
        return hasLetter && hasDigit;
    }

    private boolean safeEqualsHash(String a, String b) {
        byte[] aBytes = a == null ? new byte[0] : a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b == null ? new byte[0] : b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }

    private String sha256Base64(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar hash para código de recuperación", e);
        }
    }

    private String normalizeEmail(String s) {
        if (s == null) return null;
        return s.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeUpper(String s) {
        if (s == null) return null;
        return s.trim().toUpperCase(Locale.ROOT);
    }

    private String normalize(String s) {
        return s == null ? null : s.trim();
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private record IdentityMatch(AppUser user, Person person) {}
}
