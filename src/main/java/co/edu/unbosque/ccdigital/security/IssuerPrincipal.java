package co.edu.unbosque.ccdigital.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Implementación de {@link UserDetails} para autenticar usuarios de entidades emisoras.
 *
 * <p>Este principal expone el identificador del emisor (issuerId) y asigna la autoridad
 * {@code ROLE_ISSUER}.</p>
 */
public class IssuerPrincipal implements UserDetails {

    private final Long issuerId;
    private final String username;
    private final String passwordHash;
    private final boolean enabled;

    /**
     * Construye un principal de emisor.
     *
     * @param issuerId identificador de la entidad emisora
     * @param username usuario (generalmente email)
     * @param passwordHash hash de contraseña
     * @param enabled indica si el usuario está habilitado
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
     * @return issuerId
     */
    public Long getIssuerId() {
        return issuerId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_ISSUER"));
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return username; }

    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
