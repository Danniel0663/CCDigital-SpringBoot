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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Tus hashes en BD son bcrypt tipo "$2a$10$..." sin prefijo "{bcrypt}",
     * por eso usamos BCryptPasswordEncoder directamente.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Carga usuarios desde BD:
     * - users: login por email o full_name (admin, gobierno, etc.)
     * - entity_users: login por email (emisores)
     */
    @Bean
    public UserDetailsService userDetailsService(AppUserRepository appUserRepo,
                                                 EntityUserRepository entityUserRepo) {
        return rawUsername -> {
            String username = rawUsername == null ? "" : rawUsername.trim();

            // 1) Gobierno/Admin
            AppUser u = appUserRepo
                    .findFirstByEmailIgnoreCaseOrFullNameIgnoreCase(username, username)
                    .orElse(null);

            if (u != null) {
                if (u.getPasswordHash() == null || u.getPasswordHash().isBlank()) {
                    throw new UsernameNotFoundException("El usuario no tiene password configurado: " + username);
                }

                String role = normalizeRole(u.getRole()); // ejemplo: GOBIERNO
                boolean disabled = (u.getIsActive() != null && !u.getIsActive());

                String principalName = (u.getEmail() != null && !u.getEmail().isBlank())
                        ? u.getEmail()
                        : u.getFullName();

                User.UserBuilder b = User.withUsername(principalName)
                        .password(u.getPasswordHash())
                        .roles(role);

                if (disabled) {
                    b.disabled(true);
                }

                return b.build();
            }

            // 2) Emisor
            EntityUser eu = entityUserRepo.findByEmailIgnoreCase(username)
                    .orElseThrow(() -> new UsernameNotFoundException("Emisor no encontrado: " + username));

            if (eu.getPasswordHash() == null || eu.getPasswordHash().isBlank()) {
                throw new UsernameNotFoundException("El emisor no tiene password configurado: " + username);
            }

            boolean enabled = !(eu.getIsActive() != null && !eu.getIsActive());
            return new IssuerPrincipal(eu.getEntityId(), eu.getEmail(), eu.getPasswordHash(), enabled);
        };
    }

    private String normalizeRole(String role) {
        if (role == null) return "GOBIERNO";
        String r = role.trim();
        return r.isEmpty() ? "GOBIERNO" : r.toUpperCase();
    }

    // =========================
    // ADMIN / GOBIERNO
    // =========================
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
                        // Quita esta línea si quieres CSRF también en /api/**
                        .ignoringRequestMatchers("/api/**")
                );

        return http.build();
    }

    // =========================
    // ISSUER
    // =========================
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
