package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.entity.Person;
import co.edu.unbosque.ccdigital.service.PersonService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API REST para operaciones básicas sobre personas.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/persons")
public class PersonController {

    private final PersonService personService;

    /**
     * Constructor con inyección del servicio de personas.
     *
     * @param personService servicio de personas
     */
    public PersonController(PersonService personService) {
        this.personService = personService;
    }

    /**
     * Lista todas las personas registradas.
     *
     * @return lista de personas
     */
    @GetMapping
    public List<Person> listAll() {
        return personService.findAll();
    }

    /**
     * Obtiene una persona por su identificador.
     *
     * @param id identificador de la persona
     * @return persona si existe; 404 en caso contrario
     */
    @GetMapping("/{id}")
    public ResponseEntity<Person> getById(@PathVariable Long id) {
        return personService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Crea una persona y prepara su carpeta de almacenamiento.
     *
     * @param person entidad persona a crear
     * @return persona persistida
     */
    @PostMapping
    public Person create(@RequestBody Person person) {
        return personService.createPersonAndFolder(person);
    }
}
