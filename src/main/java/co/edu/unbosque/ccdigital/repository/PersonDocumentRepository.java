package co.edu.unbosque.ccdigital.repository;

import co.edu.unbosque.ccdigital.entity.PersonDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA para PersonDocument.
 *
 * Objetivo:
 * - Consultar documentos asociados a una persona y cargar relaciones necesarias para UI/servicios:
 *   - files (archivos del documento)
 *   - documentDefinition (catálogo del tipo de documento)
 *   - issuerEntity (entidad emisora del documento)
 *
 * Se usan "left join fetch" para:
 * - Evitar N+1 al renderizar pantallas donde se muestran títulos, entidad emisora y archivos.
 * - Permitir que el documento se cargue incluso si no tiene archivos (left join).
 *
 * "distinct" evita duplicados por el join con archivos (un documento con varios files genera varias filas).
 */
public interface PersonDocumentRepository extends JpaRepository<PersonDocument, Long> {

    /**
     * Retorna todos los documentos asociados a una persona, con sus relaciones principales cargadas:
     * - files
     * - documentDefinition
     * - issuerEntity
     *
     * Uso típico:
     * - Pantallas administrativas o del emisor donde se necesita listar documentos de una persona.
     *
     * @param personId ID interno de la persona
     * @return Lista de PersonDocument con archivos y relaciones cargadas
     */
    @Query("select distinct pd " +
           "from PersonDocument pd " +
           "left join fetch pd.files " +
           "left join fetch pd.documentDefinition " +
           "left join fetch pd.issuerEntity " +
           "where pd.person.id = :personId")
    List<PersonDocument> findByPersonIdWithFiles(@Param("personId") Long personId);

    /**
     * Retorna SOLO los documentos aprobados (reviewStatus = 'APPROVED') de una persona,
     * con sus relaciones principales cargadas.
     *
     * Uso típico:
     * - Para que el emisor pueda solicitar acceso únicamente a documentos aprobados.
     * - Para evitar que se soliciten documentos que no han pasado revisión del gobierno.
     *
     * Nota:
     * - El filtro está escrito como literal 'APPROVED'. Esto asume que reviewStatus
     *   en PersonDocument se persiste como String/Enum con ese valor exacto.
     *
     * @param personId ID interno de la persona
     * @return Lista de documentos aprobados con relaciones cargadas
     */
    @Query("select distinct pd " +
           "from PersonDocument pd " +
           "left join fetch pd.files " +
           "left join fetch pd.documentDefinition " +
           "left join fetch pd.issuerEntity " +
           "where pd.person.id = :personId and pd.reviewStatus = 'APPROVED'")
    List<PersonDocument> findApprovedByPersonIdWithFiles(@Param("personId") Long personId);

    /**
     * Obtiene un documento por su ID, cargando archivos y relaciones asociadas.
     *
     * Uso típico:
     * - Para visualizar/descargar archivos del documento sin disparar consultas adicionales.
     * - Para validaciones en servicios: confirmar que existe, revisar entidad emisora, etc.
     *
     * @param id ID del PersonDocument
     * @return Optional con el documento y relaciones cargadas
     */
    @Query("select distinct pd " +
           "from PersonDocument pd " +
           "left join fetch pd.files " +
           "left join fetch pd.documentDefinition " +
           "left join fetch pd.issuerEntity " +
           "where pd.id = :id")
    Optional<PersonDocument> findByIdWithFiles(@Param("id") Long id);
}
