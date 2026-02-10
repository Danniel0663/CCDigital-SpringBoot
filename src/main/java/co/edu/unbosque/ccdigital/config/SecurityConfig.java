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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Configuración de seguridad para la aplicación.
 *
 * <p>Define dos cadenas (filter chains) con reglas independientes:
 * <ul>
 *   <li><b>Admin/Gobierno</b>: rutas /admin/**, /api/** y /login/admin</li>
 *   <li><b>Issuer</b>: rutas /issuer/** y /login/issuer</li>
 * </ul>
 *
 * <p>La autenticación se resuelve contra base de datos:
 * <ul>
 *   <li>Tabla <code>users</code> para usuarios Admin/Gobierno (login por email o full_name)</li>
 *   <li>Tabla <code>entity_users</code> para usuarios de emisores (login por email)</li>
 * </ul>
 *
 * <p>CSRF:
 * <ul>
 *   <li>Se habilita repositorio de token por cookie para permitir formularios con soporte frontend.</li>
 *   <li>En Admin se ignora CSRF para <code>/api/**</code> (útil si el API es consumido por herramientas/scripts).
 *       Si el API se consume desde navegador con sesión, conviene mantener CSRF también en /api/**.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Define el codificador de contraseñas.
     *
     * <p>Los hashes almacenados en base de datos están en formato BCrypt
     * (por ejemplo: <code>$2a$10$...</code>) sin prefijo <code>{bcrypt}</code>,
     * por lo que se usa {@link BCryptPasswordEncoder} directamente.
     *
     * @return {@link PasswordEncoder} basado en BCrypt.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Servicio de carga de usuarios autenticables.
     *
     * <p>Reglas:
     * <ul>
     *   <li><b>Admin/Gobierno</b>: busca en <code>users</code> por email o full_name. Carga roles desde BD.</li>
     *   <li><b>Issuer</b>: busca en <code>entity_users</code> por email y expone el rol ROLE_ISSUER.</li>
     * </ul>
     *
     * <p>Notas de implementación:
     * <ul>
     *   <li>Si el usuario está inactivo (<code>is_active=false</code>), se marca como deshabilitado.</li>
     *   <li>Para Admin/Gobierno el username principal preferido es el email; si no existe, se usa fullName.</li>
     *   <li>Para Issuer se retorna un {@link IssuerPrincipal} con <code>issuerId</code> asociado.</li>
     * </ul>
     *
     * @param appUserRepo repositorio de usuarios Admin/Gobierno.
     * @param entityUserRepo repositorio de usuarios Issuer.
     * @return implementación de {@link UserDetailsService}.
     */
    @Bean
    public UserDetailsService userDetailsService(AppUserRepository appUserRepo,
                                                 EntityUserRepository entityUserRepo) {
        return rawUsername -> {
            String username = rawUsername == null ? "" : rawUsername.trim();

            // 1) Admin/Gobierno: login por email o full_name
            AppUser u = appUserRepo
                    .findFirstByEmailIgnoreCaseOrFullNameIgnoreCase(username, username)
                    .orElse(null);

            if (u != null) {
                if (u.getPasswordHash() == null || u.getPasswordHash().isBlank()) {
                    throw new UsernameNotFoundException("El usuario no tiene password configurado: " + username);
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

            // 2) Issuer: login por email
            EntityUser eu = entityUserRepo.findByEmailIgnoreCase(username)
                    .orElseThrow(() -> new UsernameNotFoundException("Emisor no encontrado: " + username));

            if (eu.getPasswordHash() == null || eu.getPasswordHash().isBlank()) {
                throw new UsernameNotFoundException("El emisor no tiene password configurado: " + username);
            }

            boolean enabled = !(eu.getIsActive() != null && !eu.getIsActive());
            return new IssuerPrincipal(eu.getEntityId(), eu.getEmail(), eu.getPasswordHash(), enabled);
        };
    }

    /**
     * Normaliza el rol leído desde base de datos para uso con {@link User#roles(String...)}.
     *
     * <p>Spring Security agrega el prefijo <code>ROLE_</code> automáticamente.
     * Por ejemplo, si en BD se almacena <code>GOBIERNO</code>, en runtime se tendrá
     * la autoridad <code>ROLE_GOBIERNO</code>.
     *
     * @param role rol leído desde BD.
     * @return rol normalizado en mayúsculas. Si es null o vacío, retorna <code>GOBIERNO</code>.
     */
    private String normalizeRole(String role) {
        if (role == null) return "GOBIERNO";
        String r = role.trim();
        return r.isEmpty() ? "GOBIERNO" : r.toUpperCase();
    }

    /**
     * Cadena de seguridad para Admin/Gobierno.
     *
     * <p>Aplica a:
     * <ul>
     *   <li><code>/admin/**</code></li>
     *   <li><code>/api/**</code></li>
     *   <li><code>/login/admin</code></li>
     * </ul>
     *
     * <p>Autorización:
     * <ul>
     *   <li><code>/login/admin</code> y <code>/error</code> permitidos sin autenticación</li>
     *   <li><code>/api/**</code> y el resto de <code>/admin/**</code> requieren ROLE_ADMIN o ROLE_GOBIERNO</li>
     * </ul>
     *
     * <p>CSRF:
     * <ul>
     *   <li>Token almacenado en cookie (HttpOnly=false) para facilitar consumo desde UI.</li>
     *   <li><code>/api/**</code> queda excluido de CSRF por configuración.</li>
     * </ul>
     *
     * @param http configuración de seguridad HTTP.
     * @return {@link SecurityFilterChain} para Admin/Gobierno.
     * @throws Exception si ocurre un error configurando la cadena.
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
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                )
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/api/**")
                );

        return http.build();
    }

    /**
     * Cadena de seguridad para Issuers (emisores).
     *
     * <p>Aplica a:
     * <ul>
     *   <li><code>/issuer/**</code></li>
     *   <li><code>/login/issuer</code></li>
     * </ul>
     *
     * <p>Autorización:
     * <ul>
     *   <li><code>/login/issuer</code> y <code>/error</code> permitidos sin autenticación</li>
     *   <li>Resto requiere ROLE_ISSUER</li>
     * </ul>
     *
     * <p>CSRF:
     * <ul>
     *   <li>Token almacenado en cookie (HttpOnly=false).</li>
     * </ul>
     *
     * @param http configuración de seguridad HTTP.
     * @return {@link SecurityFilterChain} para Issuer.
     * @throws Exception si ocurre un error configurando la cadena.
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
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                )
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                );

        return http.build();
    }
}
