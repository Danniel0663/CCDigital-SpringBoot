package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.entity.Person;
import co.edu.unbosque.ccdigital.service.PersonService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para gestión de personas (API).
 *
 * <p>Expone endpoints bajo {@code /api/persons} para listar, consultar y crear personas.</p>
 *
 * @since 3.0
 */
@RestController
@RequestMapping("/api/persons")
public class PersonController {

    private final PersonService personService;

    /**
     * Constructor del controlador.
     *
     * @param personService servicio para operaciones sobre personas
     */
    public PersonController(PersonService personService) {
        this.personService = personService;
    }

    /**
     * Lista todas las personas registradas.
     *
     * @return lista de {@link Person}
     */
    @GetMapping
    public List<Person> listAll() {
        return personService.findAll();
    }

    /**
     * Obtiene una persona por su identificador.
     *
     * @param id identificador interno de la persona
     * @return {@code 200 OK} con la persona si existe, o {@code 404 Not Found} si no existe
     */
    @GetMapping("/{id}")
    public ResponseEntity<Person> getById(@PathVariable("id") Long id) {
        return personService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Crea una nueva persona a partir de un JSON.
     *
     * @param person entidad recibida en el cuerpo del request
     * @return persona creada (incluye id asignado)
     */
    @PostMapping
    public Person create(@RequestBody Person person) {
        return personService.createPersonAndFolder(person);
    }
}
