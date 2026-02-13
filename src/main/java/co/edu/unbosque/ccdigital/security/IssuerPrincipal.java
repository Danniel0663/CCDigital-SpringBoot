package co.edu.unbosque.ccdigital.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Implementación de {@link UserDetails} para autenticar usuarios pertenecientes a entidades emisoras.
 *
 * <p>
 * Este principal expone el identificador del emisor ({@code issuerId}) y asigna la autoridad
 * {@code ROLE_ISSUER}. Es utilizado por el {@code UserDetailsService} del módulo de emisores.
 * </p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
public class IssuerPrincipal implements UserDetails {

    private static final String ROLE_ISSUER = "ROLE_ISSUER";

    private final Long issuerId;
    private final String username;
    private final String passwordHash;
    private final boolean enabled;

    /**
     * Construye un principal de emisor.
     *
     * @param issuerId identificador de la entidad emisora
     * @param username usuario de autenticación (generalmente email)
     * @param passwordHash hash de contraseña (BCrypt)
     * @param enabled indica si el usuario se encuentra habilitado
     */
    public IssuerPrincipal(Long issuerId, String username, String passwordHash, boolean enabled) {
        this.issuerId = issuerId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
    }

    /**
     * Retorna el identificador de la entidad emisora autenticada.
     *
     * @return id del emisor
     */
    public Long getIssuerId() {
        return issuerId;
    }

    /**
     * Retorna las autoridades asociadas al emisor autenticado.
     *
     * @return colección de autoridades
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(ROLE_ISSUER));
    }

    /**
     * Retorna el hash de contraseña del usuario de entidad.
     *
     * @return hash de contraseña
     */
    @Override
    public String getPassword() {
        return passwordHash;
    }

    /**
     * Retorna el username utilizado para autenticación (por ejemplo, email).
     *
     * @return username
     */
    @Override
    public String getUsername() {
        return username;
    }

    /**
     * Indica si la cuenta no ha expirado.
     *
     * @return {@code true}
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * Indica si la cuenta no está bloqueada.
     *
     * @return {@code true}
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * Indica si las credenciales no han expirado.
     *
     * @return {@code true}
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Indica si el usuario está habilitado.
     *
     * @return {@code true} si está habilitado; {@code false} en caso contrario
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
