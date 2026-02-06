package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.entity.DocumentDefinition;
import co.edu.unbosque.ccdigital.repository.DocumentDefinitionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DocumentDefinitionService {

    private final DocumentDefinitionRepository repository;

    public List<DocumentDefinition> findAllowedByIssuer(Long issuerId) {
        return repository.findAllowedByIssuer(issuerId);
    }

    public DocumentDefinitionService(DocumentDefinitionRepository repository) {
        this.repository = repository;
    }

    public List<DocumentDefinition> findAll() {
        return repository.findAll();
    }

    public Optional<DocumentDefinition> findById(Long id) {
        return repository.findById(id);
    }

    public DocumentDefinition save(DocumentDefinition def) {
        return repository.save(def);
    }
}
