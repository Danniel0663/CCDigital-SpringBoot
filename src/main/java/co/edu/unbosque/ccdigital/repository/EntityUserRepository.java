package co.edu.unbosque.ccdigital.repository;

import co.edu.unbosque.ccdigital.entity.EntityUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repositorio JPA para la entidad {@link EntityUser}.
 *
 * <p>Se utiliza para autenticación y administración de credenciales de emisores.</p>
 */
public interface EntityUserRepository extends JpaRepository<EntityUser, Long> {

    /**
     * Busca un usuario de entidad emisora por correo, ignorando mayúsculas/minúsculas.
     *
     * @param email correo a buscar
     * @return {@link Optional} con el usuario encontrado o vacío si no existe
     */
    Optional<EntityUser> findByEmailIgnoreCase(String email);
}
