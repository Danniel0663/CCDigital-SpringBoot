package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.entity.EntityStatus;
import co.edu.unbosque.ccdigital.entity.EntityType;
import co.edu.unbosque.ccdigital.entity.IssuingEntity;
import co.edu.unbosque.ccdigital.entity.DocumentDefinition;
import co.edu.unbosque.ccdigital.repository.IssuingEntityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class IssuingEntityService {

    private final IssuingEntityRepository repo;
    private final DocumentDefinitionService documentDefinitionService;

    public IssuingEntityService(IssuingEntityRepository repo,
                                DocumentDefinitionService documentDefinitionService) {
        this.repo = repo;
        this.documentDefinitionService = documentDefinitionService;
    }

    public List<IssuingEntityRepository.IssuerStats> stats() {
        return repo.findIssuerStats();
    }

    public IssuingEntity getById(Long id) {
        return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Emisor no encontrado"));
    }

    public List<IssuingEntity> findAll() {
        return repo.findAll();
    }

    @Transactional
    public IssuingEntity resolveEmitterByName(String name) {
        if (name == null || name.isBlank()) return null;

        return repo.findByEntityTypeAndNameIgnoreCase(EntityType.EMISOR, name.trim())
                .orElseGet(() -> {
                    IssuingEntity e = new IssuingEntity();
                    e.setName(name.trim());
                    e.setEntityType(EntityType.EMISOR);
                    e.setStatus(EntityStatus.APROBADA);
                    return repo.save(e);
                });
    }

    @Transactional
    public void ensureIssuerHasDocument(IssuingEntity issuer, Long documentId) {
        DocumentDefinition def = documentDefinitionService.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado"));

        if (!issuer.getDocumentDefinitions().contains(def)) {
            issuer.getDocumentDefinitions().add(def);
            repo.save(issuer); // inserta en entity_document_definitions
        }
    }

    @Transactional
    public void removeIssuerDocument(IssuingEntity issuer, Long documentId) {
        DocumentDefinition def = documentDefinitionService.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado"));

        issuer.getDocumentDefinitions().remove(def);
        repo.save(issuer);
    }
}
