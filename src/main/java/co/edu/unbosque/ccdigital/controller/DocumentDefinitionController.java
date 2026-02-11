package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.entity.DocumentDefinition;
import co.edu.unbosque.ccdigital.service.DocumentDefinitionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para la consulta del catálogo de definiciones de documentos.
 *
 * <p>Expone endpoints bajo {@code /api/document-definitions} para listar y consultar
 * definiciones de documentos disponibles en el sistema según el modelo {@link DocumentDefinition}).</p>
 *
 * <p>Este controlador está orientado a consumo por clientes web/JS, integraciones o módulos
 * internos que requieran consultar el catálogo.</p>
 *
 * @author Danniel 
 * @author Yeison
 * @since 3.0
 */
@RestController
@RequestMapping("/api/document-definitions")
public class DocumentDefinitionController {

    /**
     * Servicio de negocio encargado de consultar definiciones de documentos.
     */
    private final DocumentDefinitionService service;

    /**
     * Construye el controlador inyectando el servicio requerido.
     *
     * @param service servicio para operaciones sobre {@link DocumentDefinition}
     */
    public DocumentDefinitionController(DocumentDefinitionService service) {
        this.service = service;
    }

    /**
     * Lista todas las definiciones de documentos registradas en el sistema.
     *
     * @return lista de {@link DocumentDefinition}
     */
    @GetMapping
    public List<DocumentDefinition> listAll() {
        return service.findAll();
    }

    /**
     * Obtiene una definición de documento por su identificador.
     *
     * <p>Si existe, retorna {@code 200 OK} con el cuerpo de la definición.
     * Si no existe, retorna {@code 404 Not Found}.</p>
     *
     * @param id identificador interno de la definición
     * @return {@link ResponseEntity} con la definición encontrada o 404 si no existe
     */
    @GetMapping("/{id}")
    public ResponseEntity<DocumentDefinition> getById(@PathVariable Long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
