package co.edu.unbosque.ccdigital.repository;

import co.edu.unbosque.ccdigital.entity.IdType;
import co.edu.unbosque.ccdigital.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repositorio JPA para la entidad {@link Person}.
 *
 * <p>Extiende {@link JpaRepository} para proveer operaciones CRUD sobre la tabla {@code persons}
 * (personas registradas en el sistema).</p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
public interface PersonRepository extends JpaRepository<Person, Long> {

    /**
     * Busca una persona por tipo de identificación y número.
     *
     * <p>Este método se implementa automáticamente por Spring Data JPA a partir del nombre
     * del método, generando la consulta correspondiente.</p>
     *
     * @param idType tipo de identificación ({@link IdType})
     * @param idNumber número de identificación
     * @return {@link Optional} con la persona si existe; vacío si no existe
     */
    Optional<Person> findByIdTypeAndIdNumber(IdType idType, String idNumber);
}
