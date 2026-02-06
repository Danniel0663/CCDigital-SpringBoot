package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.entity.IdType;
import co.edu.unbosque.ccdigital.entity.Person;
import co.edu.unbosque.ccdigital.repository.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class PersonService {

    private final PersonRepository personRepository;
    private final FileStorageService fileStorageService;

    public PersonService(PersonRepository personRepository, FileStorageService fileStorageService) {
        this.personRepository = personRepository;
        this.fileStorageService = fileStorageService;
    }

    public List<Person> findAll() {
        return personRepository.findAll();
    }

    public Optional<Person> findById(Long id) {
        return personRepository.findById(id);
    }

    public Optional<Person> findByIdTypeAndNumber(IdType idType, String idNumber) {
        return personRepository.findByIdTypeAndIdNumber(idType, idNumber);
    }

    @Transactional
    public Person createPersonAndFolder(Person person) {
        Person saved = personRepository.save(person);

        // ✅ por ahora: carpeta se crea al "crear usuario" => aquí al crear persona
        fileStorageService.ensurePersonFolder(saved);

        return saved;
    }
}
