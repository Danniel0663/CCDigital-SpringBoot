package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.entity.DocumentDefinition;
import co.edu.unbosque.ccdigital.service.DocumentDefinitionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para consulta del catálogo de definiciones de documentos.
 *
 * <p>
 * Expone endpoints bajo {@code /api/document-definitions} para listar y consultar definiciones
 * disponibles en el sistema.
 * </p>
 *
 * @since 3.0
 */
@RestController
@RequestMapping("/api/document-definitions")
public class DocumentDefinitionController {

    private final DocumentDefinitionService service;

    /**
     * Constructor del controlador.
     *
     * @param service servicio para operaciones sobre {@link DocumentDefinition}
     */
    public DocumentDefinitionController(DocumentDefinitionService service) {
        this.service = service;
    }

    /**
     * Lista todas las definiciones de documentos registradas.
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
     * @param id identificador interno de la definición
     * @return {@code 200 OK} si existe, o {@code 404 Not Found} si no existe
     */
    @GetMapping("/{id}")
    public ResponseEntity<DocumentDefinition> getById(@PathVariable("id") Long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
