package co.edu.unbosque.ccdigital.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA que representa un usuario asociado a una entidad emisora.
 *
 * <p>
 * Se mapea a la tabla {@code entity_users} y se utiliza para autenticación en el portal de emisores.
 * La clave primaria corresponde a {@code entity_id}.
 * </p>
 *
 * @since 3.0
 */
@Entity
@Table(name = "entity_users")
public class EntityUser {

    /**
     * Identificador de la entidad a la que pertenece el usuario.
     *
     * <p>Columna: {@code entity_id} (PK).</p>
     */
    @Id
    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    /**
     * Nombre completo del usuario de entidad.
     */
    @Column(name = "full_name", nullable = false, length = 300)
    private String fullName;

    /**
     * Correo electrónico del usuario de entidad.
     */
    @Column(name = "email", nullable = false, length = 200)
    private String email;

    /**
     * Hash de contraseña almacenado (BCrypt).
     */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /**
     * Indicador de habilitación del usuario para autenticación.
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    /**
     * Retorna el id de la entidad asociada (entity_id).
     *
     * @return id de la entidad
     */
    public Long getEntityId() {
        return entityId;
    }

    /**
     * Establece el id de la entidad asociada (entity_id).
     *
     * @param entityId id de la entidad
     */
    public void setEntityId(Long entityId) {
        this.entityId = entityId;
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
     * Retorna el correo electrónico.
     *
     * @return correo electrónico
     */
    public String getEmail() {
        return email;
    }

    /**
     * Establece el correo electrónico.
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
}
