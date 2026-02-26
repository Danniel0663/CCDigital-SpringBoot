package co.edu.unbosque.ccdigital.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidad principal que representa una solicitud de acceso a documentos.
 *
 * Contexto:
 * - Una entidad emisora (IssuingEntity) solicita consultar uno o varios documentos de una persona (Person).
 * - El usuario (persona) puede aprobar o rechazar.
 * - Si aprueba, el emisor puede visualizar los documentos contenidos en los items de la solicitud.
 *
 * Tabla asociada: access_requests
 */
@Entity
@Table(name = "access_requests")
public class AccessRequest {

    /**
     * Identificador interno de la solicitud.
     * Se genera automáticamente en base de datos.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Entidad emisora que realiza la solicitud.
     * - Se mapea con la columna entity_id
     * - fetch=LAZY evita cargar la entidad completa si no se necesita en algunas consultas
     */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id", nullable = false)
    private IssuingEntity entity;

    /**
     * Persona propietaria de los documentos solicitados.
     * - Se mapea con person_id
     */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    /**
     * Motivo o justificación de la solicitud.
     * Longitud máxima 300 para asegurar consistencia con el esquema.
     */
    @Column(name = "purpose", nullable = false, length = 300)
    private String purpose;

    /**
     * Estado de la solicitud.
     * Se persiste como texto (EnumType.STRING).
     * Valor inicial por defecto: PENDIENTE.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccessRequestStatus status = AccessRequestStatus.PENDIENTE;

    /**
     * Fecha/hora en la que se registró la solicitud.
     *
     * <p>Se asigna desde la aplicación (ver {@link #ensureRequestedAt()}) para mantener consistencia
     * con otras fechas del flujo (por ejemplo {@code decidedAt}) y evitar desfases cuando la zona horaria
     * de la base de datos difiere de la zona usada por la JVM.</p>
     */
    @Column(name = "requested_at", updatable = false)
    private LocalDateTime requestedAt;

    /**
     * Fecha/hora en la que la solicitud fue decidida (aprobada o rechazada).
     * Se setea cuando el usuario toma una decisión.
     */
    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    /**
     * Fecha/hora de expiración de la solicitud.
     * Permite invalidar solicitudes automáticamente si se implementa una regla temporal.
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * Nota opcional registrada por el usuario al aprobar o rechazar.
     * Longitud máxima 300.
     */
    @Column(name = "decision_note", length = 300)
    private String decisionNote;

    /**
     * Lista de items (documentos) solicitados.
     *
     * - mappedBy="accessRequest": el lado propietario del FK es AccessRequestItem.accessRequest
     * - cascade=ALL: al persistir la solicitud, se persisten sus items
     * - orphanRemoval=true: si se elimina un item de la lista, se elimina en BD
     *
     * @JsonIgnore evita ciclos al serializar a JSON (por ejemplo si se expone vía API).
     */
    @OneToMany(mappedBy = "accessRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<AccessRequestItem> items = new ArrayList<>();

    /**
     * @return ID interno de la solicitud
     */
    public Long getId() { return id; }

    /**
     * @return Entidad emisora solicitante
     */
    public IssuingEntity getEntity() { return entity; }

    /**
     * @param entity Entidad emisora solicitante
     */
    public void setEntity(IssuingEntity entity) { this.entity = entity; }

    /**
     * @return Persona propietaria de los documentos solicitados
     */
    public Person getPerson() { return person; }

    /**
     * @param person Persona propietaria de los documentos solicitados
     */
    public void setPerson(Person person) { this.person = person; }

    /**
     * @return Motivo de la solicitud
     */
    public String getPurpose() { return purpose; }

    /**
     * @param purpose Motivo de la solicitud
     */
    public void setPurpose(String purpose) { this.purpose = purpose; }

    /**
     * @return Estado actual de la solicitud
     */
    public AccessRequestStatus getStatus() { return status; }

    /**
     * @param status Estado a asignar
     */
    public void setStatus(AccessRequestStatus status) { this.status = status; }

    /**
     * @return Fecha/hora de creación registrada por la BD
     */
    public LocalDateTime getRequestedAt() { return requestedAt; }

    /**
     * @param requestedAt Fecha/hora de creación de la solicitud
     */
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }

    /**
     * @return Fecha/hora en que se tomó la decisión (si aplica)
     */
    public LocalDateTime getDecidedAt() { return decidedAt; }

    /**
     * @param decidedAt Fecha/hora de decisión
     */
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }

    /**
     * @return Fecha/hora de expiración (si aplica)
     */
    public LocalDateTime getExpiresAt() { return expiresAt; }

    /**
     * @param expiresAt Fecha/hora de expiración
     */
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    /**
     * @return Nota opcional de la decisión del usuario
     */
    public String getDecisionNote() { return decisionNote; }

    /**
     * @param decisionNote Nota de decisión (opcional)
     */
    public void setDecisionNote(String decisionNote) { this.decisionNote = decisionNote; }

    /**
     * @return Items (documentos solicitados) asociados a la solicitud
     */
    public List<AccessRequestItem> getItems() { return items; }

    /**
     * Reemplaza la lista de items.
     * Si se usa orphanRemoval, cuidado: retirar items puede eliminarlos en BD.
     *
     * @param items lista de items
     */
    public void setItems(List<AccessRequestItem> items) { this.items = items; }

    /**
     * Asegura una fecha de creación consistente cuando la solicitud se persiste desde la aplicación.
     *
     * <p>Si la BD tiene un {@code DEFAULT CURRENT_TIMESTAMP} seguirá siendo un respaldo, pero este valor
     * se envía explícitamente para evitar diferencias de zona horaria entre BD y aplicación.</p>
     */
    @PrePersist
    void ensureRequestedAt() {
        if (requestedAt == null) {
            requestedAt = LocalDateTime.now();
        }
    }
}
