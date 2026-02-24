package co.edu.unbosque.ccdigital.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Entidad JPA que representa un usuario administrativo del sistema.
 *
 * <p>
 * Se mapea a la tabla {@code users} y se utiliza para autenticación y autorización en el módulo
 * administrativo (Gobierno).
 * </p>
 *
 * <p>
 * La clave primaria corresponde a {@code person_id}. El rol se almacena como valor funcional y
 * en la capa de seguridad se interpreta como {@code ROLE_<ROL>}.
 * </p>
 *
 * @since 3.0
 */
@Entity
@Table(name = "users")
public class AppUser {

    /**
     * Identificador del usuario.
     *
     * <p>Columna: {@code person_id} (PK).</p>
     */
    @Id
    @Column(name = "person_id", nullable = false)
    private Long personId;

    /**
     * Nombre completo del usuario.
     *
     * <p>Columna: {@code full_name}.</p>
     */
    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    /**
     * Correo electrónico del usuario.
     *
     * <p>Columna: {@code email}.</p>
     */
    @Column(name = "email", nullable = false, length = 200)
    private String email;

    /**
     * Hash de contraseña almacenado (BCrypt).
     *
     * <p>Columna: {@code password_hash}.</p>
     */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /**
     * Indicador de habilitación del usuario para autenticación.
     *
     * <p>Columna: {@code is_active}.</p>
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    /**
     * Rol funcional almacenado en base de datos.
     *
     * <p>Columna: {@code role}. En la capa de seguridad se interpreta como {@code ROLE_<ROL>}.</p>
     */
    @Column(name = "role", nullable = false, length = 60)
    private String role;

    /**
     * Secreto TOTP en Base32 para MFA con app autenticadora.
     *
     * <p>Columna: {@code totp_secret_base32}. Puede ser null si MFA no está configurado.</p>
     */
    @Column(name = "totp_secret_base32", length = 128)
    private String totpSecretBase32;

    /**
     * Indicador de si el MFA TOTP está activo para el usuario.
     *
     * <p>Columna: {@code totp_enabled}.</p>
     */
    @Column(name = "totp_enabled")
    private Boolean totpEnabled = Boolean.FALSE;

    /**
     * Fecha/hora de confirmación/activación del TOTP.
     *
     * <p>Columna: {@code totp_confirmed_at}.</p>
     */
    @Column(name = "totp_confirmed_at")
    private LocalDateTime totpConfirmedAt;

    /**
     * Último time-step TOTP aceptado (anti reuso del mismo código).
     *
     * <p>Columna: {@code totp_last_time_step}.</p>
     */
    @Column(name = "totp_last_time_step")
    private Long totpLastTimeStep;

    /**
     * Retorna el identificador del usuario (person_id).
     *
     * @return id del usuario
     */
    public Long getPersonId() {
        return personId;
    }

    /**
     * Establece el identificador del usuario (person_id).
     *
     * @param personId id del usuario
     */
    public void setPersonId(Long personId) {
        this.personId = personId;
    }

    /**
     * Retorna el nombre completo del usuario.
     *
     * @return nombre completo
     */
    public String getFullName() {
        return fullName;
    }

    /**
     * Establece el nombre completo del usuario.
     *
     * @param fullName nombre completo
     */
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    /**
     * Retorna el correo electrónico del usuario.
     *
     * @return correo electrónico
     */
    public String getEmail() {
        return email;
    }

    /**
     * Establece el correo electrónico del usuario.
     *
     * @param email correo electrónico
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Retorna el hash de contraseña (BCrypt).
     *
     * @return hash de contraseña
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Establece el hash de contraseña (BCrypt).
     *
     * @param passwordHash hash de contraseña
     */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * Retorna el indicador de habilitación.
     *
     * @return {@code true} si está activo; {@code false} en caso contrario
     */
    public Boolean getIsActive() {
        return isActive;
    }

    /**
     * Establece el indicador de habilitación.
     *
     * @param active {@code true} para activo; {@code false} para inactivo
     */
    public void setIsActive(Boolean active) {
        isActive = active;
    }

    /**
     * Retorna el rol funcional del usuario.
     *
     * @return rol funcional
     */
    public String getRole() {
        return role;
    }

    /**
     * Establece el rol funcional del usuario.
     *
     * @param role rol funcional
     */
    public void setRole(String role) {
        this.role = role;
    }

    /**
     * @return secreto TOTP Base32 (puede ser null)
     */
    public String getTotpSecretBase32() {
        return totpSecretBase32;
    }

    /**
     * @param totpSecretBase32 secreto TOTP Base32
     */
    public void setTotpSecretBase32(String totpSecretBase32) {
        this.totpSecretBase32 = totpSecretBase32;
    }

    /**
     * @return {@code true} si TOTP está activo; {@code false} en caso contrario
     */
    public Boolean getTotpEnabled() {
        return totpEnabled;
    }

    /**
     * @param totpEnabled indicador de activación TOTP
     */
    public void setTotpEnabled(Boolean totpEnabled) {
        this.totpEnabled = totpEnabled;
    }

    /**
     * @return fecha/hora de activación TOTP
     */
    public LocalDateTime getTotpConfirmedAt() {
        return totpConfirmedAt;
    }

    /**
     * @param totpConfirmedAt fecha/hora de activación TOTP
     */
    public void setTotpConfirmedAt(LocalDateTime totpConfirmedAt) {
        this.totpConfirmedAt = totpConfirmedAt;
    }

    /**
     * @return último time-step TOTP aceptado (anti replay)
     */
    public Long getTotpLastTimeStep() {
        return totpLastTimeStep;
    }

    /**
     * @param totpLastTimeStep último time-step TOTP aceptado (anti replay)
     */
    public void setTotpLastTimeStep(Long totpLastTimeStep) {
        this.totpLastTimeStep = totpLastTimeStep;
    }
}
