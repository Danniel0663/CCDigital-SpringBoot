package co.edu.unbosque.ccdigital.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidad JPA que representa un documento asociado a una persona.
 *
 * <p>
 * Se mapea a la tabla {@code person_documents}. Cada registro corresponde a una instancia de
 * {@link DocumentDefinition} para una {@link Person}, con metadatos del documento y soporte del
 * flujo de revisión administrativa.
 * </p>
 *
 * <p>
 * Restricción única: {@code (person_id, document_id, issued_at)} para evitar registros duplicados
 * del mismo documento en la misma fecha de expedición.
 * </p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
@Entity
@Table(
        name = "person_documents",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_person_doc_unique",
                columnNames = {"person_id", "document_id", "issued_at"}
        )
)
public class PersonDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private DocumentDefinition documentDefinition;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PersonDocumentStatus status = PersonDocumentStatus.VIGENTE;

    @Column(name = "issued_at")
    private LocalDate issueDate;

    @Column(name = "expires_at")
    private LocalDate expiryDate;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issuer_entity_id")
    private IssuingEntity issuerEntity;

    @Column(name = "submitted_by_entity_user_id")
    private Long submittedByEntityUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 10)
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;

    @Column(name = "reviewed_by_user")
    private Long reviewedByUserId;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_notes", length = 500)
    private String reviewNotes;

    @OneToMany(mappedBy = "personDocument", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FileRecord> files = new ArrayList<>();

    /**
     * Retorna el id del documento de persona.
     *
     * @return id
     */
    public Long getId() {
        return id;
    }

    /**
     * Establece el id del documento de persona.
     *
     * @param id id
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
     * Retorna la definición del documento.
     *
     * @return definición del documento
     */
    public DocumentDefinition getDocumentDefinition() {
        return documentDefinition;
    }

    /**
     * Establece la definición del documento.
     *
     * @param documentDefinition definición del documento
     */
    public void setDocumentDefinition(DocumentDefinition documentDefinition) {
        this.documentDefinition = documentDefinition;
    }

    /**
     * Retorna el estado funcional del documento.
     *
     * @return estado funcional
     */
    public PersonDocumentStatus getStatus() {
        return status;
    }

    /**
     * Establece el estado funcional del documento.
     *
     * @param status estado funcional
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
     * @return fecha/hora de creación
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Setter disponible por compatibilidad; el valor es gestionado por base de datos.
     *
     * @param createdAt fecha/hora de creación
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Retorna la entidad emisora asociada (si aplica).
     *
     * @return entidad emisora o {@code null}
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
     * Retorna el id del usuario de entidad que radicó el documento.
     *
     * @return id del usuario de entidad o {@code null}
     */
    public Long getSubmittedByEntityUserId() {
        return submittedByEntityUserId;
    }

    /**
     * Establece el id del usuario de entidad que radicó el documento.
     *
     * @param submittedByEntityUserId id del usuario de entidad
     */
    public void setSubmittedByEntityUserId(Long submittedByEntityUserId) {
        this.submittedByEntityUserId = submittedByEntityUserId;
    }

    /**
     * Retorna el estado del flujo de revisión.
     *
     * @return estado de revisión
     */
    public ReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    /**
     * Establece el estado del flujo de revisión.
     *
     * @param reviewStatus estado de revisión
     */
    public void setReviewStatus(ReviewStatus reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    /**
     * Retorna el id del usuario revisor (si aplica).
     *
     * @return id del revisor o {@code null}
     */
    public Long getReviewedByUserId() {
        return reviewedByUserId;
    }

    /**
     * Establece el id del usuario revisor (si aplica).
     *
     * @param reviewedByUserId id del revisor
     */
    public void setReviewedByUserId(Long reviewedByUserId) {
        this.reviewedByUserId = reviewedByUserId;
    }

    /**
     * Retorna la fecha/hora de revisión (si aplica).
     *
     * @return fecha/hora de revisión o {@code null}
     */
    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    /**
     * Establece la fecha/hora de revisión (si aplica).
     *
     * @param reviewedAt fecha/hora de revisión
     */
    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    /**
     * Retorna las notas del revisor.
     *
     * @return notas de revisión o {@code null}
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
     * Retorna los archivos asociados al documento.
     *
     * @return lista de archivos
     */
    public List<FileRecord> getFiles() {
        return files;
    }

    /**
     * Establece los archivos asociados al documento.
     *
     * @param files lista de archivos
     */
    public void setFiles(List<FileRecord> files) {
        this.files = files;
    }

    /**
     * Asocia un archivo a este documento y mantiene consistencia bidireccional.
     *
     * @param file archivo a asociar
     */
    public void addFile(FileRecord file) {
        files.add(file);
        file.setPersonDocument(this);
    }

    /**
     * Remueve un archivo de este documento y mantiene consistencia bidireccional.
     *
     * @param file archivo a remover
     */
    public void removeFile(FileRecord file) {
        files.remove(file);
        file.setPersonDocument(null);
    }
}
