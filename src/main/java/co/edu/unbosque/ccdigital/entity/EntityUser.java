package co.edu.unbosque.ccdigital.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Representa un usuario asociado a una entidad emisora, persistido en la tabla {@code entity_users}.
 *
 * <p>El identificador corresponde a {@code entity_id}. Este modelo se usa para autenticación
 * en el portal de emisores.</p>
 */
@Entity
@Table(name = "entity_users")
public class EntityUser {

    @Id
    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "full_name", nullable = false, length = 300)
    private String fullName;

    @Column(name = "email", nullable = false, length = 200)
    private String email;

    /**
     * Hash de contraseña (BCrypt).
     */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { isActive = active; }
}
