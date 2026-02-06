package co.edu.unbosque.ccdigital.repository;

import co.edu.unbosque.ccdigital.entity.EntityStatus;
import co.edu.unbosque.ccdigital.entity.EntityType;
import co.edu.unbosque.ccdigital.entity.IssuingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import co.edu.unbosque.ccdigital.entity.EntityStatus;
import co.edu.unbosque.ccdigital.entity.EntityType;
import java.util.List;
import java.util.Optional;

public interface IssuingEntityRepository extends JpaRepository<IssuingEntity, Long> {

    Optional<IssuingEntity> findByEntityTypeAndNameIgnoreCase(EntityType entityType, String name);

    // ✅ Para el módulo issuer: listar emisores aprobados
    List<IssuingEntity> findByEntityTypeAndStatusOrderByNameAsc(EntityType entityType, EntityStatus status);

    // Para el listado admin con conteos
    interface IssuerStats {
        Long getId();
        String getName();
        Long getDocumentosEmitidos();
        Long getTiposDocumento();
    }

    // ✅ Sin text blocks (compatibilidad)
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
