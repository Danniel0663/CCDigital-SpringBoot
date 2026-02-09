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
 * Servicio de negocio para la gestión de entidades emisoras ({@link IssuingEntity}).
 *
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
@Service
public class IssuingEntityService {

    /**
     * Repositorio de acceso a datos para {@link IssuingEntity}.
     */
    private final IssuingEntityRepository repo;

    /**
     * Servicio del catálogo de documentos, usado para validar/recuperar definiciones.
     */
    private final DocumentDefinitionService documentDefinitionService;

    /**
     * Crea una instancia del servicio.
     *
     * @param repo repositorio de emisores
     * @param documentDefinitionService servicio del catálogo de documentos
     */
    public IssuingEntityService(IssuingEntityRepository repo,
                                DocumentDefinitionService documentDefinitionService) {
        this.repo = repo;
        this.documentDefinitionService = documentDefinitionService;
    }

    /**
     * Retorna estadísticas agregadas de emisores para el módulo administrativo.
     *
     * <p>La consulta es provista por {@link IssuingEntityRepository#findIssuerStats()} y retorna una proyección
     * ({@link IssuingEntityRepository.IssuerStats})</p>
     *
     * @return lista de estadísticas por emisor
     */
    public List<IssuingEntityRepository.IssuerStats> stats() {
        return repo.findIssuerStats();
    }

    /**
     * Obtiene un emisor por id.
     *
     * @param id id del emisor
     * @return emisor encontrado
     * @throws IllegalArgumentException si no existe un emisor con el id indicado
     */
    public IssuingEntity getById(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Emisor no encontrado"));
    }

    /**
     * Retorna todos los emisores registrados (sin filtros).
     *
     * @return lista completa de emisores
     */
    public List<IssuingEntity> findAll() {
        return repo.findAll();
    }

    /**
     * Lista emisores aprobados para el módulo "issuer".
     *
     * <p>Filtra por {@link EntityType#EMISOR} y {@link EntityStatus#APROBADA}, ordenando por nombre ascendente.</p>
     *
     * @return lista de emisores aprobados
     */
    @Transactional(readOnly = true)
    public List<IssuingEntity> listApprovedEmitters() {
        return repo.findByEntityTypeAndStatusOrderByNameAsc(EntityType.EMISOR, EntityStatus.APROBADA);
    }

    /**
     * <p>Si el nombre es {@code null} o está en blanco, retorna {@code null}.</p>
     *
     * @param name nombre del emisor
     * @return emisor existente o recién creado; {@code null} si el nombre es inválido
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
     * Asegura que un emisor tenga asociado un documento permitido.
     *
     * <p>Valida que el documento exista en el catálogo, revisa si ya está asociado al emisor
     * y si no lo está, lo agrega y persiste el emisor.</p>
     *
     * @param issuer emisor al cual se le desea asociar el documento
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
     * Remueve la asociación entre un emisor y un documento permitido.
     *
     * <p>Elimina el documento de la colección {@code issuer.documentDefinitions} por id y persiste el emisor.</p>
     *
     * @param issuer emisor del cual se desea remover el documento
     * @param documentId id del documento a remover
     */
    @Transactional
    public void removeIssuerDocument(IssuingEntity issuer, Long documentId) {
        issuer.getDocumentDefinitions().removeIf(d -> d.getId() != null && d.getId().equals(documentId));
        repo.save(issuer);
    }
}
