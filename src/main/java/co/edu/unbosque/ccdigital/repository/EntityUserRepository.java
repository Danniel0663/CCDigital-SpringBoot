package co.edu.unbosque.ccdigital.repository;

import co.edu.unbosque.ccdigital.entity.EntityUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repositorio JPA para {@link EntityUser}.
 *
 * <p>
 * Se utiliza principalmente para autenticación y consultas de usuarios del portal de emisores,
 * sobre la tabla {@code entity_users}.
 * </p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
public interface EntityUserRepository extends JpaRepository<EntityUser, Long> {

    /**
     * Busca un usuario de entidad por correo electrónico, ignorando mayúsculas/minúsculas.
     *
     * @param email correo a buscar
     * @return {@link Optional} con el usuario encontrado o vacío si no existe
     */
    Optional<EntityUser> findByEmailIgnoreCase(String email);
}
