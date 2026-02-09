package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.entity.IdType;
import co.edu.unbosque.ccdigital.entity.Person;
import co.edu.unbosque.ccdigital.repository.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Servicio de dominio para gestión de personas.
 *
 * <p>Incluye operaciones de consulta y creación. Al crear una persona, se asegura
 * la creación de su carpeta en almacenamiento.</p>
 */
@Service
public class PersonService {

    private final PersonRepository personRepository;
    private final FileStorageService fileStorageService;

    public PersonService(PersonRepository personRepository, FileStorageService fileStorageService) {
        this.personRepository = personRepository;
        this.fileStorageService = fileStorageService;
    }

    /**
     * Lista todas las personas registradas.
     *
     * @return lista de personas
     */
    public List<Person> findAll() {
        return personRepository.findAll();
    }

    /**
     * Busca una persona por id.
     *
     * @param id identificador de la persona
     * @return {@link Optional} con la persona si existe
     */
    public Optional<Person> findById(Long id) {
        return personRepository.findById(id);
    }

    /**
     * Busca una persona por tipo y número de identificación.
     *
     * @param idType tipo de identificación
     * @param idNumber número de identificación
     * @return {@link Optional} con la persona si existe
     */
    public Optional<Person> findByIdTypeAndNumber(IdType idType, String idNumber) {
        return personRepository.findByIdTypeAndIdNumber(idType, idNumber);
    }

    /**
     * Crea una persona y asegura la existencia de su carpeta en el sistema de archivos.
     *
     * @param person entidad a crear
     * @return persona persistida
     */
    @Transactional
    public Person createPersonAndFolder(Person person) {
        Person saved = personRepository.save(person);
        fileStorageService.ensurePersonFolder(saved);
        return saved;
    }
}
