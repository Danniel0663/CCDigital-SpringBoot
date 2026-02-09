package co.edu.unbosque.ccdigital.config;

import co.edu.unbosque.ccdigital.entity.AppUser;
import co.edu.unbosque.ccdigital.entity.EntityUser;
import co.edu.unbosque.ccdigital.repository.AppUserRepository;
import co.edu.unbosque.ccdigital.repository.EntityUserRepository;
import co.edu.unbosque.ccdigital.security.IssuerPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de seguridad de la aplicación.
 *
 * <p>Define los mecanismos de autenticación y autorización para los perfiles de uso:</p>
 * <ul>
 *   <li>Administración y APIs: rutas bajo {@code /admin/**} y {@code /api/**}</li>
 *   <li>Emisores: rutas bajo {@code /issuer/**}</li>
 * </ul>
 *
 * <p>La autenticación se resuelve desde base de datos mediante {@link UserDetailsService}:</p>
 * <ul>
 *   <li>Usuarios administrativos: tabla de usuarios de aplicación</li>
 *   <li>Usuarios emisores: tabla de usuarios de entidad emisora</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Proveedor de codificación de contraseñas basado en BCrypt.
     *
     * <p>Se asume que los hashes en base de datos corresponden al formato BCrypt (por ejemplo, {@code $2a$10...}).</p>
     *
     * @return codificador de contraseñas
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Servicio de carga de usuarios para autenticación.
     *
     * <p>Resuelve credenciales de:</p>
     * <ul>
     *   <li>Usuarios administrativos: búsqueda por email o nombre completo</li>
     *   <li>Usuarios emisores: búsqueda por email</li>
     * </ul>
     *
     * @param appUserRepo repositorio de usuarios administrativos
     * @param entityUserRepo repositorio de usuarios emisores
     * @return servicio de detalles de usuario
     */
    @Bean
    public UserDetailsService userDetailsService(AppUserRepository appUserRepo,
                                                 EntityUserRepository entityUserRepo) {

        return rawUsername -> {
            String username = rawUsername == null ? "" : rawUsername.trim();

            AppUser u = appUserRepo
                    .findFirstByEmailIgnoreCaseOrFullNameIgnoreCase(username, username)
                    .orElse(null);

            if (u != null) {
                if (u.getPasswordHash() == null || u.getPasswordHash().isBlank()) {
                    throw new UsernameNotFoundException("El usuario no tiene contraseña configurada: " + username);
                }

                String role = normalizeRole(u.getRole());
                boolean disabled = (u.getIsActive() != null && !u.getIsActive());

                String principalName = (u.getEmail() != null && !u.getEmail().isBlank())
                        ? u.getEmail()
                        : u.getFullName();

                User.UserBuilder builder = User.withUsername(principalName)
                        .password(u.getPasswordHash())
                        .roles(role);

                if (disabled) {
                    builder.disabled(true);
                }

                return builder.build();
            }

            EntityUser eu = entityUserRepo.findByEmailIgnoreCase(username)
                    .orElseThrow(() -> new UsernameNotFoundException("Emisor no encontrado: " + username));

            if (eu.getPasswordHash() == null || eu.getPasswordHash().isBlank()) {
                throw new UsernameNotFoundException("El emisor no tiene contraseña configurada: " + username);
            }

            boolean enabled = !(eu.getIsActive() != null && !eu.getIsActive());
            return new IssuerPrincipal(eu.getEntityId(), eu.getEmail(), eu.getPasswordHash(), enabled);
        };
    }

    /**
     * Normaliza el rol para su uso con {@code roles(...)} en Spring Security.
     *
     * @param role rol almacenado en persistencia
     * @return rol en mayúsculas; por defecto {@code GOBIERNO}
     */
    private String normalizeRole(String role) {
        if (role == null) return "GOBIERNO";
        String r = role.trim();
        if (r.isEmpty()) return "GOBIERNO";
        return r.toUpperCase();
    }

    /**
     * Cadena de seguridad para rutas administrativas y API.
     *
     * @param http configuración de seguridad HTTP
     * @return filtro de seguridad construido
     * @throws Exception errores de configuración
     */
    @Bean
    @Order(1)
    public SecurityFilterChain adminChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/admin/**", "/api/**", "/login/admin")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login/admin", "/error").permitAll()
                        .requestMatchers("/api/**").hasAnyRole("ADMIN", "GOBIERNO")
                        .anyRequest().hasAnyRole("ADMIN", "GOBIERNO")
                )
                .formLogin(form -> form
                        .loginPage("/login/admin")
                        .loginProcessingUrl("/login/admin")
                        .defaultSuccessUrl("/admin/dashboard", true)
                        .failureUrl("/login/admin?error")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessUrl("/login/admin?logout")
                )
                .csrf(csrf -> csrf.disable());

        return http.build();
    }

    /**
     * Cadena de seguridad para rutas de emisor.
     *
     * @param http configuración de seguridad HTTP
     * @return filtro de seguridad construido
     * @throws Exception errores de configuración
     */
    @Bean
    @Order(2)
    public SecurityFilterChain issuerChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/issuer/**", "/login/issuer")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login/issuer", "/error").permitAll()
                        .anyRequest().hasRole("ISSUER")
                )
                .formLogin(form -> form
                        .loginPage("/login/issuer")
                        .loginProcessingUrl("/login/issuer")
                        .defaultSuccessUrl("/issuer", true)
                        .failureUrl("/login/issuer?error")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/issuer/logout")
                        .logoutSuccessUrl("/login/issuer?logout")
                )
                .csrf(csrf -> csrf.disable());

        return http.build();
    }
}
