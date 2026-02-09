package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.entity.Person;
import co.edu.unbosque.ccdigital.service.PersonService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para la gestión de personas (API).
 *
 * <p>Expone endpoints bajo {@code /api/persons}</p>
 *
 * <p>Este controlador está diseñado para consumo por clientes externos
 * La lógica de negocio se delega a {@link PersonService}.</p>
 *
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
@RestController
@RequestMapping("/api/persons")
public class PersonController {

    /**
     * Servicio de negocio encargado de operaciones sobre {@link Person}.
     */
    private final PersonService personService;

    /**
     * Construye el controlador inyectando el servicio requerido.
     *
     * @param personService servicio para operaciones sobre personas
     */
    public PersonController(PersonService personService) {
        this.personService = personService;
    }

    /**
     * Lista todas las personas registradas en el sistema.
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
     * <p>Si existe, retorna {@code 200 OK} con el cuerpo de la persona.
     * Si no existe, retorna {@code 404 Not Found}.</p>
     *
     * @param id identificador interno de la persona
     * @return {@link ResponseEntity} con la persona encontrada o 404 si no existe
     */
    @GetMapping("/{id}")
    public ResponseEntity<Person> getById(@PathVariable Long id) {
        return personService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Crea una nueva persona a partir del JSON recibido en el cuerpo de la petición.
     *
     * <p>La creación delega a {@link PersonService#createPersonAndFolder(Person)}</p>
     *
     * @param person entidad {@link Person} recibida desde el request body (JSON)
     * @return la persona creada (normalmente incluye el id asignado)
     */
    @PostMapping
    public Person create(@RequestBody Person person) {
        return personService.createPersonAndFolder(person);
    }
}
