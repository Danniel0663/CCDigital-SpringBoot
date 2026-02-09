package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.entity.IdType;
import co.edu.unbosque.ccdigital.entity.Person;
import co.edu.unbosque.ccdigital.repository.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Servicio de negocio para la gestión de personas ({@link Person}).
 *
 * <p>Esta clase encapsula el acceso a datos de {@link PersonRepository} y la lógica complementaria
 * relacionada con el ciclo de vida de una persona en el sistema.</p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
@Service
public class PersonService {

    /**
     * Repositorio JPA para {@link Person}.
     */
    private final PersonRepository personRepository;

    /**
     * Servicio de almacenamiento de archivos, usado para crear la carpeta base de cada persona.
     */
    private final FileStorageService fileStorageService;

    /**
     * Constructor con inyección de dependencias.
     *
     * @param personRepository repositorio de personas
     * @param fileStorageService servicio de almacenamiento de archivos
     */
    public PersonService(PersonRepository personRepository, FileStorageService fileStorageService) {
        this.personRepository = personRepository;
        this.fileStorageService = fileStorageService;
    }

    /**
     * Retorna todas las personas registradas.
     *
     * @return lista de personas
     */
    public List<Person> findAll() {
        return personRepository.findAll();
    }

    /**
     * Busca una persona por su id.
     *
     * @param id id de la persona
     * @return {@link Optional} con la persona si existe; vacío si no existe
     */
    public Optional<Person> findById(Long id) {
        return personRepository.findById(id);
    }

    /**
     * Busca una persona por tipo y número de identificación.
     *
     * <p>Este método es útil para flujos donde el usuario ingresa su identificación en un formulario
     * (por ejemplo, módulo issuer o sincronización).</p>
     *
     * @param idType tipo de identificación (ej: {@link IdType#CC})
     * @param idNumber número de identificación
     * @return {@link Optional} con la persona si existe; vacío si no existe
     */
    public Optional<Person> findByIdTypeAndNumber(IdType idType, String idNumber) {
        return personRepository.findByIdTypeAndIdNumber(idType, idNumber);
    }

    /**
     * Crea una persona y asegura la creación de su carpeta en el sistema de archivos.
     *
     * <p>Se ejecuta dentro de una transacción para asegurar consistencia en la creación de la persona.
     * Ten en cuenta que la creación de la carpeta en disco no participa de la transacción de BD.</p>
     *
     * @param person entidad {@link Person} a crear
     * @return persona persistida (con id asignado)
     */
    @Transactional
    public Person createPersonAndFolder(Person person) {
        Person saved = personRepository.save(person);

        fileStorageService.ensurePersonFolder(saved);

        return saved;
    }
}
