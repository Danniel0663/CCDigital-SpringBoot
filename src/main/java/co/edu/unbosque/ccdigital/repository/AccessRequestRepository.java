package co.edu.unbosque.ccdigital.repository;

import co.edu.unbosque.ccdigital.entity.AccessRequest;
import co.edu.unbosque.ccdigital.entity.AccessRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA para la entidad AccessRequest.
 *
 * Objetivo:
 * - Proveer consultas optimizadas para listar solicitudes de acceso con su "detalle"
 *   (entidad solicitante, persona solicitada e items/documentos).
 *
 * Importante:
 * - Se usan "join fetch" para evitar el problema N+1 cuando la vista necesita
 *   acceder a relaciones (entity, person, items, personDocument, documentDefinition).
 * - Se usa "distinct" porque los joins (especialmente con items) pueden duplicar filas
 *   en el resultado, y distinct ayuda a devolver cada AccessRequest una sola vez.
 *
 * Nota sobre orden:
 * - Se ordena por requestedAt desc para mostrar primero las solicitudes más recientes.
 */
public interface AccessRequestRepository extends JpaRepository<AccessRequest, Long> {

    /**
     * Lista solicitudes para una persona específica, trayendo el detalle necesario para la UI del usuario:
     * - entity (quién solicita)
     * - items -> personDocument -> documentDefinition (qué documentos se solicitan)
     *
     * join fetch ar.entity:
     * - Se necesita para mostrar el nombre de la entidad solicitante en la vista.
     *
     * left join fetch ar.items:
     * - La solicitud puede existir y aún así tener items (en práctica debería tener),
     *   pero se usa left join para ser defensivo.
     *
     * left join fetch i.personDocument / pd.documentDefinition:
     * - La vista lista títulos de documento.
     *
     * @param personId ID interno de la persona (tabla persons)
     * @return Lista de solicitudes con detalle para renderizar en UI
     */
    @Query("select distinct ar " +
           "from AccessRequest ar " +
           "join fetch ar.entity e " +
           "left join fetch ar.items i " +
           "left join fetch i.personDocument pd " +
           "left join fetch pd.documentDefinition dd " +
           "where ar.person.id = :personId " +
           "order by ar.requestedAt desc")
    List<AccessRequest> findForPersonWithDetails(@Param("personId") Long personId);

    /**
     * Lista solicitudes creadas por una entidad emisora específica (para UI del emisor),
     * trayendo detalle necesario:
     * - person (a quién se solicita)
     * - items -> personDocument -> documentDefinition (qué documentos se solicitaron)
     *
     * join fetch ar.person:
     * - Se necesita para mostrar el nombre y documento de la persona en la lista del emisor.
     *
     * @param entityId ID interno de la entidad emisora (tabla issuing_entities)
     * @return Lista de solicitudes del emisor con detalle
     */
    @Query("select distinct ar " +
           "from AccessRequest ar " +
           "join fetch ar.person p " +
           "left join fetch ar.items i " +
           "left join fetch i.personDocument pd " +
           "left join fetch pd.documentDefinition dd " +
           "where ar.entity.id = :entityId " +
           "order by ar.requestedAt desc")
    List<AccessRequest> findForEntityWithDetails(@Param("entityId") Long entityId);

    /**
     * Obtiene una solicitud por ID, cargando el detalle completo:
     * - entity y person
     * - items y sus documentos
     *
     * Uso típico:
     * - Validaciones de seguridad/negocio en el servicio (ej: decide, viewDocument).
     * - Evita consultas adicionales al acceder a relaciones.
     *
     * @param id ID de la solicitud
     * @return Optional con la solicitud y sus relaciones cargadas
     */
    @Query("select distinct ar " +
           "from AccessRequest ar " +
           "join fetch ar.entity e " +
           "join fetch ar.person p " +
           "left join fetch ar.items i " +
           "left join fetch i.personDocument pd " +
           "left join fetch pd.documentDefinition dd " +
           "where ar.id = :id")
    Optional<AccessRequest> findByIdWithDetails(@Param("id") Long id);

    /**
     * Busca una solicitud por:
     * - ID de solicitud
     * - ID de la persona propietaria
     * - Estado actual (por ejemplo, PENDIENTE)
     *
     * Uso típico:
     * - Para asegurar que una acción (aprobar/rechazar) solo se ejecute si:
     *   1) La solicitud es del usuario correcto
     *   2) Está en un estado permitido para esa transición
     *
     * Nota:
     * - Esta consulta NO hace fetch joins; si la vista o servicio requiere detalles,
     *   use findByIdWithDetails o las consultas "WithDetails".
     *
     * @param id       ID de la solicitud
     * @param personId ID de la persona dueña de los documentos
     * @param status   Estado esperado (ej: PENDIENTE)
     * @return Optional con la solicitud si cumple las condiciones
     */
    Optional<AccessRequest> findByIdAndPerson_IdAndStatus(Long id, Long personId, AccessRequestStatus status);
}
