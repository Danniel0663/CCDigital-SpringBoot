package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.entity.DocumentDefinition;
import co.edu.unbosque.ccdigital.service.DocumentDefinitionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API REST para consultar definiciones de documentos.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/document-definitions")
public class DocumentDefinitionController {

    private final DocumentDefinitionService service;

    /**
     * Constructor con inyección del servicio de definiciones.
     *
     * @param service servicio de definiciones de documentos
     */
    public DocumentDefinitionController(DocumentDefinitionService service) {
        this.service = service;
    }

    /**
     * Lista todas las definiciones de documento.
     *
     * @return lista de definiciones
     */
    @GetMapping
    public List<DocumentDefinition> listAll() {
        return service.findAll();
    }

    /**
     * Obtiene una definición de documento por su identificador.
     *
     * @param id identificador de la definición
     * @return definición si existe; 404 en caso contrario
     */
    @GetMapping("/{id}")
    public ResponseEntity<DocumentDefinition> getById(@PathVariable Long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
