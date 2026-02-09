package co.edu.unbosque.ccdigital.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Representa un usuario administrativo del sistema, persistido en la tabla {@code users}.
 *
 * <p>Este modelo se usa para autenticación y autorización en el módulo administrativo.</p>
 */
@Entity
@Table(name = "users")
public class AppUser {

    /**
     * Identificador del usuario. En esta tabla se utiliza {@code person_id} como clave primaria.
     */
    @Id
    @Column(name = "person_id", nullable = false)
    private Long personId;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "email", nullable = false, length = 200)
    private String email;

    /**
     * Hash de contraseña (BCrypt).
     */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /**
     * Indica si el usuario está habilitado para autenticarse.
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    /**
     * Rol funcional almacenado en BD. En Spring Security se interpreta como {@code ROLE_<ROL>}.
     */
    @Column(name = "role", nullable = false, length = 60)
    private String role;

    public Long getPersonId() { return personId; }
    public void setPersonId(Long personId) { this.personId = personId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { isActive = active; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
