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
 * <p>Extiende {@link JpaRepository} para proveer operaciones CRUD sobre la tabla {@code person_documents}
 * (documentos asociados a personas).</p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
public interface PersonDocumentRepository extends JpaRepository<PersonDocument, Long> {

    /**
     * Retorna los documentos asociados a una persona.
     *
     * <p>Se utiliza típicamente para mostrar el detalle de la persona con su listado de documentos,
     * evitando múltiples consultas adicionales (N+1).</p>
     *
     * @param personId id de la persona
     * @return lista de {@link PersonDocument} con sus relaciones cargadas
     */
    @Query("select distinct pd " +
           "from PersonDocument pd " +
           "left join fetch pd.files " +
           "left join fetch pd.documentDefinition " +
           "left join fetch pd.issuerEntity " +
           "where pd.person.id = :personId")
    List<PersonDocument> findByPersonIdWithFiles(@Param("personId") Long personId);

    /**
     * Obtiene un {@link PersonDocument} por su id.
     *
     * <p>Útil para endpoints de detalle/descarga donde se requiere validar el archivo asociado
     * al documento y acceder a metadatos sin disparar consultas adicionales.</p>
     *
     * @param id id del {@link PersonDocument}
     * @return {@link Optional} con el documento si existe; vacío si no existe
     */
    @Query("select pd " +
           "from PersonDocument pd " +
           "left join fetch pd.files " +
           "left join fetch pd.documentDefinition " +
           "left join fetch pd.issuerEntity " +
           "where pd.id = :id")
    Optional<PersonDocument> findByIdWithFiles(@Param("id") Long id);
}
