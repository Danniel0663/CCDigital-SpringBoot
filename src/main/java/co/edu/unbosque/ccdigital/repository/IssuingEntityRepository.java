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
 * <p>Este repositorio centraliza el acceso a datos de la tabla {@code entities} (emisores)
 * e incluye métodos para:</p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
public interface IssuingEntityRepository extends JpaRepository<IssuingEntity, Long> {

    /**
     * Busca una entidad emisora por su tipo y por su nombre, ignorando mayúsculas y minúsculas.
     *
     * <p>Útil para evitar duplicados en el registro (por ejemplo, si quieres validar si ya existe un emisor
     * antes de crearlo).</p>
     *
     * @param entityType tipo de entidad (por ejemplo {@link EntityType#EMISOR})
     * @param name nombre de la entidad
     * @return {@link Optional} con la entidad si existe; vacío si no existe
     */
    Optional<IssuingEntity> findByEntityTypeAndNameIgnoreCase(EntityType entityType, String name);

    /**
     * Lista entidades por tipo y estado, ordenadas por nombre ascendente.
     *
     * <p>Se usa típicamente en el módulo "issuer" para listar emisores aprobados
     * (por ejemplo: {@code entityType=EMISOR} y {@code status=APROBADA}).</p>
     *
     * @param entityType tipo de entidad (por ejemplo {@link EntityType#EMISOR})
     * @param status estado de aprobación (por ejemplo {@link EntityStatus#APROBADA})
     * @return lista de emisores filtrados y ordenados por nombre
     */
    List<IssuingEntity> findByEntityTypeAndStatusOrderByNameAsc(EntityType entityType, EntityStatus status);

    /**
     * Proyección (interface-based projection) para el resumen estadístico de emisores.
     *
     * <p>Los métodos deben coincidir con los alias definidos en la consulta nativa
     * de {@link #findIssuerStats()}.</p>
     */
    interface IssuerStats {

        /**
         * @return id del emisor
         */
        Long getId();

        /**
         * @return nombre del emisor
         */
        String getName();

        /**
         * @return cantidad de documentos emitidos/radicados por el emisor (conteo de person_documents)
         */
        Long getDocumentosEmitidos();

        /**
         * @return cantidad de tipos de documento autorizados para el emisor (distinct en tabla puente)
         */
        Long getTiposDocumento();
    }

    /**
     * Retorna estadísticas agregadas por emisor para el módulo administrativo.
     *
     * <p>La consulta calcula:</p>
     *
     * <p>Incluye emisores aunque no tengan documentos emitidos (por uso de {@code LEFT JOIN}).</p>
     * <p>Filtra únicamente entidades con {@code entity_type = 'EMISOR'}.</p>
     * <p>Ordena por documentos emitidos descendente y luego por nombre ascendente.</p>
     *
     * @return lista de estadísticas por emisor
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
