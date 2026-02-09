package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.entity.DocumentDefinition;
import co.edu.unbosque.ccdigital.entity.EntityStatus;
import co.edu.unbosque.ccdigital.entity.EntityType;
import co.edu.unbosque.ccdigital.entity.IssuingEntity;
import co.edu.unbosque.ccdigital.repository.IssuingEntityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Servicio de dominio para gestión de entidades emisoras.
 *
 * <p>Incluye operaciones de consulta, resolución/creación por nombre y administración
 * de documentos habilitados por emisor.</p>
 */
@Service
public class IssuingEntityService {

    private final IssuingEntityRepository repo;
    private final DocumentDefinitionService documentDefinitionService;

    public IssuingEntityService(IssuingEntityRepository repo,
                                DocumentDefinitionService documentDefinitionService) {
        this.repo = repo;
        this.documentDefinitionService = documentDefinitionService;
    }

    /**
     * Obtiene estadísticas agregadas de emisores.
     *
     * @return lista de estadísticas
     */
    public List<IssuingEntityRepository.IssuerStats> stats() {
        return repo.findIssuerStats();
    }

    /**
     * Obtiene un emisor por id.
     *
     * @param id identificador del emisor
     * @return emisor
     * @throws IllegalArgumentException si no existe
     */
    public IssuingEntity getById(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Emisor no encontrado"));
    }

    /**
     * Lista todas las entidades.
     *
     * @return lista completa
     */
    public List<IssuingEntity> findAll() {
        return repo.findAll();
    }

    /**
     * Lista emisores aprobados.
     *
     * @return lista de emisores con estado APROBADA
     */
    @Transactional(readOnly = true)
    public List<IssuingEntity> listApprovedEmitters() {
        return repo.findByEntityTypeAndStatusOrderByNameAsc(EntityType.EMISOR, EntityStatus.APROBADA);
    }

    /**
     * Resuelve un emisor por nombre. Si no existe, lo crea como emisor aprobado.
     *
     * @param name nombre del emisor
     * @return entidad emisora encontrada o creada; null si el nombre es vacío
     */
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

    /**
     * Asegura que el emisor tenga habilitado un tipo de documento.
     *
     * @param issuer emisor
     * @param documentId id del documento del catálogo
     * @throws IllegalArgumentException si el documento no existe
     */
    @Transactional
    public void ensureIssuerHasDocument(IssuingEntity issuer, Long documentId) {
        DocumentDefinition def = documentDefinitionService.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado"));

        boolean exists = issuer.getDocumentDefinitions().stream()
                .anyMatch(d -> d.getId() != null && d.getId().equals(def.getId()));

        if (!exists) {
            issuer.getDocumentDefinitions().add(def);
            repo.save(issuer);
        }
    }

    /**
     * Elimina la relación entre un emisor y un tipo de documento.
     *
     * @param issuer emisor
     * @param documentId id del documento a remover
     */
    @Transactional
    public void removeIssuerDocument(IssuingEntity issuer, Long documentId) {
        issuer.getDocumentDefinitions().removeIf(d -> d.getId() != null && d.getId().equals(documentId));
        repo.save(issuer);
    }
}
