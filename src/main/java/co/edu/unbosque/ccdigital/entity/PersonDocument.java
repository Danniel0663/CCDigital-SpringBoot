package co.edu.unbosque.ccdigital.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidad JPA que representa un documento asociado a una persona (radicación/registro).
 *
 * <p>Se mapea a la tabla {@code person_documents}. Cada registro representa una "instancia"
 * de un {@link DocumentDefinition} para una {@link Person}, incluyendo metadatos del documento
 * (estado, fechas, notas) y un flujo de revisión (review workflow).</p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
@Entity
@Table(
        name = "person_documents",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_person_doc_unique",
                        columnNames = {"person_id", "document_id", "issued_at"}
                )
        }
)
public class PersonDocument {

    /**
     * Identificador interno del documento de persona (PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Persona propietaria del documento.
     *
     * <p>Columna: {@code person_id} (FK a {@code persons.id}).</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    /**
     * Definición/catálogo del documento.
     *
     * <p>Columna: {@code document_id} (FK a {@code documents.id}).</p>
     *
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private DocumentDefinition documentDefinition;

    /**
     * Estado funcional del documento (por ejemplo: vigente, vencido, etc.).
     *
     * <p>Columna: {@code status}. Por defecto: {@link PersonDocumentStatus#VIGENTE}.</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PersonDocumentStatus status = PersonDocumentStatus.VIGENTE;

    /**
     * Fecha de expedición/emisión del documento.
     *
     * <p>Columna: {@code issued_at}. Hace parte de la restricción única.</p>
     */
    @Column(name = "issued_at")
    private LocalDate issueDate;

    /**
     * Fecha de vencimiento del documento (si aplica).
     *
     * <p>Columna: {@code expires_at}.</p>
     */
    @Column(name = "expires_at")
    private LocalDate expiryDate;

    /**
     * Notas generales asociadas al documento (opcional).
     *
     * <p>Columna: {@code notes}. Longitud máxima: 500 caracteres.</p>
     */
    @Column(name = "notes", length = 500)
    private String notes;

    /**
     * Fecha/hora de creación del registro (gestionada por base de datos).
     *
     * <p>Columna: {@code created_at}. No insertable ni actualizable desde JPA.</p>
     */
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    /**
     * Entidad emisora (issuer) que radicó o cargó el documento (si aplica).
     *
     * <p>Columna: {@code issuer_entity_id} (FK a {@code entities.id}).</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issuer_entity_id")
    private IssuingEntity issuerEntity;

    /**
     * Identificador del usuario (de la entidad emisora) que radicó el documento.
     *
     * <p>Columna: {@code submitted_by_entity_user_id}. Por ahora se maneja como id numérico
     * sin relación directa a una tabla de usuarios.</p>
     */
    @Column(name = "submitted_by_entity_user_id")
    private Long submittedByEntityUserId;

    /**
     * Estado del flujo de revisión administrativa.
     *
     * <p>Columna: {@code review_status}. Por defecto: {@link ReviewStatus#PENDING}.</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 10)
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;

    /**
     * Identificador del usuario que revisó el documento (si aplica).
     *
     * <p>Columna: {@code reviewed_by_user}.</p>
     */
    @Column(name = "reviewed_by_user")
    private Long reviewedByUserId;

    /**
     * Fecha/hora en la que se realizó la revisión (si aplica).
     *
     * <p>Columna: {@code reviewed_at}.</p>
     */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /**
     * Notas del revisor (comentarios de aprobación/rechazo).
     *
     * <p>Columna: {@code review_notes}. Longitud máxima: 500 caracteres.</p>
     */
    @Column(name = "review_notes", length = 500)
    private String reviewNotes;

    /**
     * Archivos asociados a este documento de persona.
     *
     * <p>Relación One-to-Many con {@link FileRecord}, mapeada por {@code personDocument}.
     * Se usa {@code cascade = ALL} para persistir/eliminar en cascada y {@code orphanRemoval = true}
     * para eliminar archivos huérfanos cuando se remueven de la colección.</p>
     */
    @OneToMany(mappedBy = "personDocument", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FileRecord> files = new ArrayList<>();

    /**
     * Retorna el id del documento de persona.
     *
     * @return id del documento
     */
    public Long getId() {
        return id;
    }

    /**
     * Establece el id del documento de persona.
     *
     * @param id id del documento
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Retorna la persona propietaria.
     *
     * @return persona
     */
    public Person getPerson() {
        return person;
    }

    /**
     * Establece la persona propietaria.
     *
     * @param person persona
     */
    public void setPerson(Person person) {
        this.person = person;
    }

    /**
     * Retorna la definición/catálogo del documento.
     *
     * @return definición del documento
     */
    public DocumentDefinition getDocumentDefinition() {
        return documentDefinition;
    }

    /**
     * Establece la definición/catálogo del documento.
     *
     * @param documentDefinition definición del documento
     */
    public void setDocumentDefinition(DocumentDefinition documentDefinition) {
        this.documentDefinition = documentDefinition;
    }

    /**
     * Retorna el estado funcional del documento.
     *
     * @return estado del documento
     */
    public PersonDocumentStatus getStatus() {
        return status;
    }

    /**
     * Establece el estado funcional del documento.
     *
     * @param status estado del documento
     */
    public void setStatus(PersonDocumentStatus status) {
        this.status = status;
    }

    /**
     * Retorna la fecha de expedición.
     *
     * @return fecha de expedición
     */
    public LocalDate getIssueDate() {
        return issueDate;
    }

    /**
     * Establece la fecha de expedición.
     *
     * @param issueDate fecha de expedición
     */
    public void setIssueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
    }

    /**
     * Retorna la fecha de vencimiento.
     *
     * @return fecha de vencimiento
     */
    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    /**
     * Establece la fecha de vencimiento.
     *
     * @param expiryDate fecha de vencimiento
     */
    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    /**
     * Retorna las notas generales del documento.
     *
     * @return notas
     */
    public String getNotes() {
        return notes;
    }

    /**
     * Establece las notas generales del documento.
     *
     * @param notes notas
     */
    public void setNotes(String notes) {
        this.notes = notes;
    }

    /**
     * Retorna la fecha/hora de creación del registro.
     *
     * @return fecha de creación
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Establece la fecha/hora de creación.
     *
     * <p>Generalmente la base de datos gestiona este valor.</p>
     *
     * @param createdAt fecha de creación
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Retorna la entidad emisora asociada (si aplica).
     *
     * @return entidad emisora
     */
    public IssuingEntity getIssuerEntity() {
        return issuerEntity;
    }

    /**
     * Establece la entidad emisora asociada.
     *
     * @param issuerEntity entidad emisora
     */
    public void setIssuerEntity(IssuingEntity issuerEntity) {
        this.issuerEntity = issuerEntity;
    }

    /**
     * Retorna el id del usuario (de la entidad) que radicó el documento.
     *
     * @return id del usuario de entidad
     */
    public Long getSubmittedByEntityUserId() {
        return submittedByEntityUserId;
    }

    /**
     * Establece el id del usuario (de la entidad) que radicó el documento.
     *
     * @param submittedByEntityUserId id del usuario de entidad
     */
    public void setSubmittedByEntityUserId(Long submittedByEntityUserId) {
        this.submittedByEntityUserId = submittedByEntityUserId;
    }

    /**
     * Retorna el estado del workflow de revisión.
     *
     * @return estado de revisión
     */
    public ReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    /**
     * Establece el estado del workflow de revisión.
     *
     * @param reviewStatus estado de revisión
     */
    public void setReviewStatus(ReviewStatus reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    /**
     * Retorna el id del usuario que revisó el documento.
     *
     * @return id del revisor
     */
    public Long getReviewedByUserId() {
        return reviewedByUserId;
    }

    /**
     * Establece el id del usuario que revisó el documento.
     *
     * @param reviewedByUserId id del revisor
     */
    public void setReviewedByUserId(Long reviewedByUserId) {
        this.reviewedByUserId = reviewedByUserId;
    }

    /**
     * Retorna la fecha/hora de revisión.
     *
     * @return fecha/hora de revisión
     */
    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    /**
     * Establece la fecha/hora de revisión.
     *
     * @param reviewedAt fecha/hora de revisión
     */
    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    /**
     * Retorna las notas del revisor.
     *
     * @return notas de revisión
     */
    public String getReviewNotes() {
        return reviewNotes;
    }

    /**
     * Establece las notas del revisor.
     *
     * @param reviewNotes notas de revisión
     */
    public void setReviewNotes(String reviewNotes) {
        this.reviewNotes = reviewNotes;
    }

    /**
     * Retorna la lista de archivos asociados.
     *
     * @return lista de archivos
     */
    public List<FileRecord> getFiles() {
        return files;
    }

    /**
     * Establece la lista de archivos asociados.
     *
     * @param files lista de archivos
     */
    public void setFiles(List<FileRecord> files) {
        this.files = files;
    }

    /**
     * Asocia un archivo a este documento, manteniendo consistencia bidireccional.
     *
     * @param file archivo a agregar
     */
    public void addFile(FileRecord file) {
        files.add(file);
        file.setPersonDocument(this);
    }

    /**
     * Remueve un archivo de este documento, manteniendo consistencia bidireccional.
     *
     * @param file archivo a remover
     */
    public void removeFile(FileRecord file) {
        files.remove(file);
        file.setPersonDocument(null);
    }
}
