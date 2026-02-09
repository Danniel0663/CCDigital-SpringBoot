package co.edu.unbosque.ccdigital.repository;

import co.edu.unbosque.ccdigital.entity.DocumentDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repositorio JPA para la entidad {@link DocumentDefinition}.
 *
 * <p>Incluye consultas específicas para obtener el catálogo de documentos permitido
 * para una entidad emisora.</p>
 */
public interface DocumentDefinitionRepository extends JpaRepository<DocumentDefinition, Long> {

    /**
     * Obtiene la lista de definiciones de documento permitidas para un emisor, a partir
     * de la tabla de relación {@code entity_document_definitions}.
     *
     * @param issuerId identificador del emisor (entity_id)
     * @return lista de documentos permitidos, ordenados por título
     */
    @Query(value =
            "SELECT d.* " +
            "FROM documents d " +
            "JOIN entity_document_definitions edd ON edd.document_id = d.id " +
            "WHERE edd.entity_id = :issuerId " +
            "ORDER BY d.title",
            nativeQuery = true)
    List<DocumentDefinition> findAllowedByIssuer(@Param("issuerId") Long issuerId);
}
