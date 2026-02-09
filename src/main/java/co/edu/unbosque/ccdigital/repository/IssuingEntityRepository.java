package co.edu.unbosque.ccdigital.repository;

import co.edu.unbosque.ccdigital.entity.EntityStatus;
import co.edu.unbosque.ccdigital.entity.EntityType;
import co.edu.unbosque.ccdigital.entity.IssuingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA para la entidad {@link IssuingEntity}.
 *
 * <p>Incluye búsquedas por atributos funcionales (tipo/estado) y una consulta de
 * agregación para estadísticas de emisores.</p>
 */
public interface IssuingEntityRepository extends JpaRepository<IssuingEntity, Long> {

    /**
     * Busca una entidad por tipo y nombre, ignorando mayúsculas/minúsculas.
     *
     * @param entityType tipo de entidad
     * @param name nombre de la entidad
     * @return {@link Optional} con la entidad encontrada o vacío si no existe
     */
    Optional<IssuingEntity> findByEntityTypeAndNameIgnoreCase(EntityType entityType, String name);

    /**
     * Lista entidades filtrando por tipo y estado, ordenadas por nombre.
     *
     * @param entityType tipo de entidad
     * @param status estado de entidad
     * @return lista de entidades
     */
    List<IssuingEntity> findByEntityTypeAndStatusOrderByNameAsc(EntityType entityType, EntityStatus status);

    /**
     * Proyección para estadísticas agregadas de emisores.
     */
    interface IssuerStats {
        Long getId();
        String getName();
        Long getDocumentosEmitidos();
        Long getTiposDocumento();
    }

    /**
     * Retorna estadísticas de emisores:
     * <ul>
     *   <li>Total de documentos emitidos (person_documents asociados)</li>
     *   <li>Total de tipos de documento habilitados (relación entity_document_definitions)</li>
     * </ul>
     *
     * @return lista de estadísticas ordenadas por documentos emitidos descendente y nombre ascendente
     */
    @Query(value =
            "SELECT " +
            "  e.id AS id, " +
            "  e.name AS name, " +
            "  COUNT(pd.id) AS documentosEmitidos, " +
            "  COUNT(DISTINCT edd.document_id) AS tiposDocumento " +
            "FROM entities e " +
            "LEFT JOIN person_documents pd ON pd.issuer_entity_id = e.id " +
            "LEFT JOIN entity_document_definitions edd ON edd.entity_id = e.id " +
            "WHERE e.entity_type = 'EMISOR' " +
            "GROUP BY e.id, e.name " +
            "ORDER BY documentosEmitidos DESC, e.name ASC",
            nativeQuery = true)
    List<IssuerStats> findIssuerStats();
}
