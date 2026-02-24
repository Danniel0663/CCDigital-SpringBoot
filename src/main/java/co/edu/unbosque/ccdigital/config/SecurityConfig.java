package co.edu.unbosque.ccdigital.config;

import co.edu.unbosque.ccdigital.entity.AppUser;
import co.edu.unbosque.ccdigital.entity.EntityUser;
import co.edu.unbosque.ccdigital.repository.AppUserRepository;
import co.edu.unbosque.ccdigital.repository.EntityUserRepository;
import co.edu.unbosque.ccdigital.security.IssuerPrincipal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
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
 * <p>
 * Define cadenas de filtros independientes por tipo de usuario (Admin, Issuer y User) y
 * proveedores de autenticación asociados a cada fuente de datos.
 * </p>
 *
 * <h2>Cadenas de filtros</h2>
 * <ul>
 *   <li><strong>Admin</strong>: rutas {@code /admin/**} y {@code /login/admin}.</li>
 *   <li><strong>Issuer</strong>: rutas {@code /issuer/**} y {@code /login/issuer}.</li>
 *   <li><strong>User</strong>: rutas {@code /user/**} y {@code /login/user} (incluye endpoints AJAX en {@code /user/auth/**}).</li>
 *   <li><strong>Default</strong>: rutas públicas (home y recursos estáticos).</li>
 * </ul>
 *
 * <h2>Roles esperados</h2>
 * <ul>
 *   <li>Admin: {@code ROLE_GOBIERNO}</li>
 *   <li>Issuer: {@code ROLE_ISSUER}</li>
 *   <li>User: {@code ROLE_USER}</li>
 * </ul>
 *
 * @since 1.0
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Encoder de contraseñas para hashes BCrypt.
     *
     * @return encoder BCrypt
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ---------------------------------------------------------------------
    // Admin (tabla users)
    // ---------------------------------------------------------------------

    /**
     * {@link UserDetailsService} para administradores.
     *
     * <p>
     * Busca por email o nombre (ignorando mayúsculas/minúsculas), valida estado activo y construye
     * un usuario de Spring Security con rol basado en el campo {@code role}.
     * </p>
     *
     * @param repo repositorio de usuarios administradores
     * @return servicio de carga de usuario para Admin
     */
    @Bean("adminUserDetailsService")
    public UserDetailsService adminUserDetailsService(AppUserRepository repo) {
        return username -> {
            AppUser u = repo.findFirstByEmailIgnoreCaseOrFullNameIgnoreCase(username, username)
                    .filter(x -> Boolean.TRUE.equals(x.getIsActive()))
                    .orElseThrow(() -> new UsernameNotFoundException("Admin no encontrado: " + username));

            String role = (u.getRole() == null || u.getRole().isBlank()) ? "GOBIERNO" : u.getRole().trim();
            if (role.startsWith("ROLE_")) {
                role = role.substring("ROLE_".length());
            }

            return User.withUsername(u.getEmail() != null ? u.getEmail() : username)
                    .password(u.getPasswordHash())
                    .roles(role)
                    .build();
        };
    }

    /**
     * {@link AuthenticationProvider} para autenticación de administradores.
     *
     * @param uds servicio de carga de usuario Admin
     * @param encoder encoder de contraseñas
     * @return proveedor de autenticación basado en DAO
     */
    @Bean("adminAuthProvider")
    public AuthenticationProvider adminAuthProvider(
            @Qualifier("adminUserDetailsService") UserDetailsService uds,
            PasswordEncoder encoder
    ) {
        DaoAuthenticationProvider p = new DaoAuthenticationProvider();
        p.setUserDetailsService(uds);
        p.setPasswordEncoder(encoder);
        return p;
    }

    // ---------------------------------------------------------------------
    // Issuer (tabla entity_users)
    // ---------------------------------------------------------------------

    /**
     * {@link UserDetailsService} para emisores (Issuer).
     *
     * <p>
     * Valida que el usuario esté activo, tenga hash de contraseña y esté asociado a un {@code entity_id}.
     * Devuelve un {@link IssuerPrincipal} que expone el identificador del emisor (entity_id).
     * </p>
     *
     * @param repo repositorio de usuarios emisores
     * @return servicio de carga de usuario para Issuer
     */
    @Bean("issuerUserDetailsService")
    public UserDetailsService issuerUserDetailsService(EntityUserRepository repo) {
        return username -> {
            EntityUser u = repo.findByEmailIgnoreCase(username)
                    .filter(x -> Boolean.TRUE.equals(x.getIsActive()))
                    .orElseThrow(() -> new UsernameNotFoundException("Issuer no encontrado: " + username));

            if (u.getPasswordHash() == null || u.getPasswordHash().isBlank()) {
                throw new UsernameNotFoundException("Issuer sin contraseña configurada: " + username);
            }
            if (u.getEntityId() == null) {
                throw new UsernameNotFoundException("Issuer sin entity_id asociado: " + username);
            }

            return new IssuerPrincipal(
                    u.getEntityId(),
                    u.getEmail(),
                    u.getPasswordHash(),
                    true
            );
        };
    }

    /**
     * {@link AuthenticationProvider} para autenticación de emisores.
     *
     * @param uds servicio de carga de usuario Issuer
     * @param encoder encoder de contraseñas
     * @return proveedor de autenticación basado en DAO
     */
    @Bean("issuerAuthProvider")
    public AuthenticationProvider issuerAuthProvider(
            @Qualifier("issuerUserDetailsService") UserDetailsService uds,
            PasswordEncoder encoder
    ) {
        DaoAuthenticationProvider p = new DaoAuthenticationProvider();
        p.setUserDetailsService(uds);
        p.setPasswordEncoder(encoder);
        return p;
    }

    // ---------------------------------------------------------------------
    // Filter chains
    // ---------------------------------------------------------------------

    /**
     * Cadena de seguridad para administración.
     *
     * @param http builder de seguridad HTTP
     * @param adminAuthProvider proveedor de autenticación Admin
     * @return {@link SecurityFilterChain} para rutas de administración
     * @throws Exception en caso de error de configuración
     */
    @Bean
    @Order(1)
    public SecurityFilterChain adminSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("adminAuthProvider") AuthenticationProvider adminAuthProvider
    ) throws Exception {

        http.securityMatcher("/admin/**", "/login/admin", "/admin/logout")
                .authenticationProvider(adminAuthProvider)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login/admin", "/admin/logout").permitAll()
                        .anyRequest().hasRole("GOBIERNO")
                )
                .formLogin(form -> form
                        .loginPage("/login/admin")
                        .loginProcessingUrl("/login/admin")
                        .defaultSuccessUrl("/admin/dashboard", true)
                        .failureUrl("/login/admin?error=true")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessUrl("/login/admin?logout=true")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                )
                .sessionManagement(session -> session
                        .invalidSessionUrl("/login/admin?expired=true")
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authEx) ->
                                response.sendRedirect("/login/admin?expired=true"))
                        .accessDeniedHandler((request, response, accessDeniedEx) ->
                                response.sendRedirect("/login/admin?denied=true"))
                );

        return http.build();
    }

    /**
     * Cadena de seguridad para emisores (Issuer).
     *
     * @param http builder de seguridad HTTP
     * @param issuerAuthProvider proveedor de autenticación Issuer
     * @return {@link SecurityFilterChain} para rutas de emisor
     * @throws Exception en caso de error de configuración
     */
    @Bean
    @Order(2)
    public SecurityFilterChain issuerSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("issuerAuthProvider") AuthenticationProvider issuerAuthProvider
    ) throws Exception {

        http.securityMatcher("/issuer/**", "/login/issuer", "/issuer/logout")
                .authenticationProvider(issuerAuthProvider)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login/issuer", "/issuer/logout").permitAll()
                        .anyRequest().hasRole("ISSUER")
                )
                .formLogin(form -> form
                        .loginPage("/login/issuer")
                        .loginProcessingUrl("/login/issuer")
                        .defaultSuccessUrl("/issuer", true)
                        .failureUrl("/login/issuer?error=true")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/issuer/logout")
                        .logoutSuccessUrl("/login/issuer?logout=true")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                )
                .sessionManagement(session -> session
                        .invalidSessionUrl("/login/issuer?expired=true")
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authEx) ->
                                response.sendRedirect("/login/issuer?expired=true"))
                        .accessDeniedHandler((request, response, accessDeniedEx) ->
                                response.sendRedirect("/login/issuer?denied=true"))
                );

        return http.build();
    }

    /**
     * Cadena de seguridad para usuarios finales (User).
     *
     * <p>
     * Los endpoints {@code /user/auth/**} se consumen típicamente vía AJAX (por ejemplo, login con proof),
     * por lo que se excluyen de protección CSRF.
     * </p>
     *
     * @param http builder de seguridad HTTP
     * @return {@link SecurityFilterChain} para rutas de usuario final
     * @throws Exception en caso de error de configuración
     */
    @Bean
    @Order(3)
    public SecurityFilterChain userSecurityFilterChain(HttpSecurity http) throws Exception {

        http.securityMatcher("/user/**", "/login/user", "/user/logout")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login/user", "/user/auth/**", "/user/logout").permitAll()
                        .anyRequest().hasRole("USER")
                )
                .csrf(csrf -> csrf.ignoringRequestMatchers("/user/auth/**"))
                .formLogin(form -> form
                        .loginPage("/login/user")
                        .defaultSuccessUrl("/user/dashboard", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/user/logout")
                        .logoutSuccessUrl("/login/user?logout=true")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                )
                .sessionManagement(session -> session
                        .invalidSessionUrl("/login/user?expired=true")
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authEx) ->
                                response.sendRedirect("/login/user?expired=true"))
                        .accessDeniedHandler((request, response, accessDeniedEx) ->
                                response.sendRedirect("/login/user?denied=true"))
                );

        return http.build();
    }

    /**
     * Cadena por defecto para recursos públicos.
     *
     * @param http builder de seguridad HTTP
     * @return {@link SecurityFilterChain} para rutas públicas
     * @throws Exception en caso de error de configuración
     */
    @Bean
    @Order(99)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/", "/login",
                        "/css/**", "/js/**", "/images/**", "/webjars/**"
                ).permitAll()
                .anyRequest().permitAll()
        );
        return http.build();
    }
}
