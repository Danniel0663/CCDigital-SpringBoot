package co.edu.unbosque.ccdigital.repository;

import co.edu.unbosque.ccdigital.entity.DocumentDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repositorio JPA para la entidad {@link DocumentDefinition}.
 *
 * <p>Extiende {@link JpaRepository} para proveer operaciones CRUD sobre la tabla {@code documents}
 * (definiciones/catálogo de documentos).</p>
 *
 * <p>Incluye una consulta personalizada para obtener los documentos que un emisor específico
 * tiene permitidos, basada en la tabla puente {@code entity_document_definitions}.</p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
public interface DocumentDefinitionRepository extends JpaRepository<DocumentDefinition, Long> {

    /**
     * Retorna las definiciones de documentos permitidas para un emisor.
     *
     * <p>La autorización se define por la tabla puente {@code entity_document_definitions},
     * que relaciona:</p>
     *
     * <p>La lista se retorna ordenada por {@code d.title}.</p>
     *
     * @param issuerId id del emisor (entity) para el cual se consultan documentos permitidos
     * @return lista de {@link DocumentDefinition} permitidas para el emisor
     */
    @Query(value = "SELECT d.* " +
            "FROM documents d " +
            "JOIN entity_document_definitions edd ON edd.document_id = d.id " +
            "WHERE edd.entity_id = :issuerId " +
            "ORDER BY d.title", nativeQuery = true)
    List<DocumentDefinition> findAllowedByIssuer(@Param("issuerId") Long issuerId);
}
