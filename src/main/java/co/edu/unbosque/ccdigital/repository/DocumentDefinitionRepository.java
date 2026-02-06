package co.edu.unbosque.ccdigital.repository;

import co.edu.unbosque.ccdigital.entity.DocumentDefinition;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentDefinitionRepository extends JpaRepository<DocumentDefinition, Long> {

    @Query(value = "SELECT d.* " +
            "FROM documents d " +
            "JOIN entity_document_definitions edd ON edd.document_id = d.id " +
            "WHERE edd.entity_id = :issuerId " +
            "ORDER BY d.title", nativeQuery = true)
    List<DocumentDefinition> findAllowedByIssuer(@Param("issuerId") Long issuerId);
}
