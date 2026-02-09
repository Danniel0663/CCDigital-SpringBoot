package co.edu.unbosque.ccdigital.repository;

import co.edu.unbosque.ccdigital.entity.PersonDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA para la entidad {@link PersonDocument}.
 *
 * <p>Incluye consultas con {@code fetch join} para cargar archivos y relaciones
 * asociadas evitando problemas de inicialización perezosa.</p>
 */
public interface PersonDocumentRepository extends JpaRepository<PersonDocument, Long> {

    /**
     * Lista documentos de una persona incluyendo:
     * <ul>
     *   <li>Archivos asociados</li>
     *   <li>Definición del documento</li>
     *   <li>Entidad emisora</li>
     * </ul>
     *
     * <p>El {@code distinct} evita duplicados por la relación uno-a-muchos con archivos.</p>
     *
     * @param personId identificador de la persona
     * @return lista de documentos de la persona con relaciones cargadas
     */
    @Query("select distinct pd " +
           "from PersonDocument pd " +
           "left join fetch pd.files " +
           "left join fetch pd.documentDefinition " +
           "left join fetch pd.issuerEntity " +
           "where pd.person.id = :personId")
    List<PersonDocument> findByPersonIdWithFiles(@Param("personId") Long personId);

    /**
     * Obtiene un documento por id incluyendo archivos, definición y emisor.
     *
     * @param id identificador del documento de persona
     * @return {@link Optional} con el documento si existe
     */
    @Query("select pd " +
           "from PersonDocument pd " +
           "left join fetch pd.files " +
           "left join fetch pd.documentDefinition " +
           "left join fetch pd.issuerEntity " +
           "where pd.id = :id")
    Optional<PersonDocument> findByIdWithFiles(@Param("id") Long id);
}
