package co.edu.unbosque.ccdigital.repository;

import co.edu.unbosque.ccdigital.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repositorio JPA para {@link AppUser}.
 *
 * <p>
 * Administra operaciones CRUD sobre la tabla {@code users} y expone consultas auxiliares
 * usadas principalmente en el proceso de autenticación del módulo administrativo.
 * </p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /**
     * Busca el primer usuario que coincida por correo electrónico o por nombre completo,
     * ignorando mayúsculas/minúsculas.
     *
     * <p>
     * Se utiliza para permitir autenticación con email o con el valor de {@code full_name}.
     * </p>
     *
     * @param email correo electrónico a comparar
     * @param fullName nombre completo a comparar
     * @return {@link Optional} con el usuario encontrado, o vacío si no existe coincidencia
     */
    Optional<AppUser> findFirstByEmailIgnoreCaseOrFullNameIgnoreCase(String email, String fullName);
}
