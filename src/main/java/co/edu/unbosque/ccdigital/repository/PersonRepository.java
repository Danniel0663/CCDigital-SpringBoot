package co.edu.unbosque.ccdigital.repository;

import co.edu.unbosque.ccdigital.entity.IdType;
import co.edu.unbosque.ccdigital.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repositorio JPA para la entidad {@link Person}.
 */
public interface PersonRepository extends JpaRepository<Person, Long> {

    /**
     * Busca una persona por tipo y número de identificación.
     *
     * @param idType tipo de identificación
     * @param idNumber número de identificación
     * @return {@link Optional} con la persona si existe
     */
    Optional<Person> findByIdTypeAndIdNumber(IdType idType, String idNumber);
}
