package co.edu.unbosque.ccdigital.repository;

import co.edu.unbosque.ccdigital.entity.PersonDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA para {@link PersonDocument}.
 *
 * <p>
 * Provee operaciones CRUD sobre la tabla {@code person_documents} (documentos asociados a personas).
 * Incluye consultas con {@code fetch join} para reducir el problema de N+1 cuando se requiere
 * acceder a relaciones (archivos, definición, emisor).
 * </p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
public interface PersonDocumentRepository extends JpaRepository<PersonDocument, Long> {

    /**
     * Retorna los documentos asociados a una persona, cargando relaciones necesarias para UI.
     *
     * <p>
     * Se utiliza típicamente para mostrar el detalle de la persona con su listado de documentos,
     * minimizando consultas adicionales.
     * </p>
     *
     * @param personId id de la persona
     * @return lista de {@link PersonDocument} con relaciones cargadas
     */
    @Query("select distinct pd " +
           "from PersonDocument pd " +
           "left join fetch pd.files " +
           "left join fetch pd.documentDefinition " +
           "left join fetch pd.issuerEntity " +
           "where pd.person.id = :personId")
    List<PersonDocument> findByPersonIdWithFiles(@Param("personId") Long personId);

    /**
     * Obtiene un {@link PersonDocument} por su id, incluyendo relaciones requeridas.
     *
     * <p>
     * Se usa en escenarios de detalle/descarga donde se requiere acceder a archivos y metadatos
     * sin disparar consultas adicionales.
     * </p>
     *
     * <p>
     * Se usa {@code distinct} para evitar problemas de multiplicidad por el {@code fetch join} de colecciones.
     * </p>
     *
     * @param id id del documento de persona
     * @return {@link Optional} con el documento si existe; vacío si no existe
     */
    @Query("select distinct pd " +
           "from PersonDocument pd " +
           "left join fetch pd.files " +
           "left join fetch pd.documentDefinition " +
           "left join fetch pd.issuerEntity " +
           "where pd.id = :id")
    Optional<PersonDocument> findByIdWithFiles(@Param("id") Long id);
}
