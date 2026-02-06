package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.entity.DocumentDefinition;
import co.edu.unbosque.ccdigital.service.DocumentDefinitionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/document-definitions")
public class DocumentDefinitionController {

    private final DocumentDefinitionService service;

    public DocumentDefinitionController(DocumentDefinitionService service) {
        this.service = service;
    }

    @GetMapping
    public List<DocumentDefinition> listAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentDefinition> getById(@PathVariable Long id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
