package co.edu.unbosque.ccdigital.repository;

import co.edu.unbosque.ccdigital.entity.IdType;
import co.edu.unbosque.ccdigital.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repositorio JPA para {@link Person}.
 *
 * <p>
 * Provee operaciones CRUD sobre la tabla {@code persons}. Incluye una consulta derivada para
 * ubicar una persona por su tipo y número de identificación.
 * </p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
public interface PersonRepository extends JpaRepository<Person, Long> {

    /**
     * Busca una persona por número de identificación.
     *
     * <p>
     * Se utiliza en el flujo de registro de usuarios finales para enlazar la cuenta
     * de acceso con una persona previamente cargada en {@code persons}.
     * </p>
     *
     * @param idNumber número de identificación
     * @return {@link Optional} con la persona si existe; vacío si no existe
     */
    Optional<Person> findByIdNumber(String idNumber);

    /**
     * Busca una persona por tipo de identificación y número.
     *
     * <p>
     * Este método se implementa automáticamente por Spring Data JPA a partir del nombre del método.
     * </p>
     *
     * @param idType tipo de identificación ({@link IdType})
     * @param idNumber número de identificación
     * @return {@link Optional} con la persona si existe; vacío si no existe
     */
    Optional<Person> findByIdTypeAndIdNumber(IdType idType, String idNumber);
}
