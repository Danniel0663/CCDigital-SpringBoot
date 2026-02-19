package co.edu.unbosque.ccdigital.entity;

import jakarta.persistence.*;

/**
 * Entidad que representa un ítem (detalle) dentro de una solicitud de acceso.
 *
 * Cada AccessRequestItem enlaza:
 * - Una solicitud de acceso (AccessRequest)
 * - Un documento específico de la persona (PersonDocument) que se está solicitando consultar
 *
 * Relación:
 * - Muchos items pertenecen a una misma solicitud (ManyToOne hacia AccessRequest)
 * - Muchos items pueden referenciar documentos distintos de la misma persona (ManyToOne hacia PersonDocument)
 *
 * Tabla asociada: access_request_items
 */
@Entity
@Table(name = "access_request_items")
public class AccessRequestItem {

    /**
     * Identificador interno del ítem.
     * Se genera automáticamente en base de datos.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Solicitud a la que pertenece este ítem.
     * - optional=false: siempre debe existir una solicitud asociada
     * - fetch=LAZY: se carga bajo demanda para evitar consultas innecesarias
     */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "access_request_id", nullable = false)
    private AccessRequest accessRequest;

    /**
     * Documento de la persona que se está solicitando consultar.
     * - optional=false: el ítem siempre debe apuntar a un PersonDocument válido
     * - fetch=LAZY: se carga bajo demanda
     */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "person_document_id", nullable = false)
    private PersonDocument personDocument;

    /**
     * @return ID del ítem
     */
    public Long getId() { return id; }

    /**
     * @return Solicitud asociada a este ítem
     */
    public AccessRequest getAccessRequest() { return accessRequest; }

    /**
     * Asigna la solicitud a la que pertenece el ítem.
     * En general, cuando se construyen items desde AccessRequestService,
     * se setea para mantener la relación consistente en memoria antes de persistir.
     *
     * @param accessRequest solicitud padre
     */
    public void setAccessRequest(AccessRequest accessRequest) { this.accessRequest = accessRequest; }

    /**
     * @return Documento de la persona solicitado en este ítem
     */
    public PersonDocument getPersonDocument() { return personDocument; }

    /**
     * Asigna el PersonDocument que se solicita consultar.
     *
     * @param personDocument documento asociado a la persona
     */
    public void setPersonDocument(PersonDocument personDocument) { this.personDocument = personDocument; }
}
