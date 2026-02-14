package co.edu.unbosque.ccdigital.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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
}
