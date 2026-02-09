package co.edu.unbosque.ccdigital.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Documento asociado a una persona, persistido en la tabla {@code person_documents}.
 *
 * <p>Incluye estado de vigencia, fechas, notas, emisor (si aplica) y estado de revisión.</p>
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Persona propietaria del documento.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    /**
     * Definición del documento (catálogo).
     */
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

    /**
     * Entidad emisora que remitió el documento, cuando el flujo proviene del portal de emisores.
     */
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

    /**
     * Archivos asociados al documento.
     *
     * <p>Se utiliza relación bidireccional. Los métodos {@link #addFile(FileRecord)}
     * y {@link #removeFile(FileRecord)} aseguran consistencia entre ambas entidades.</p>
     */
    @OneToMany(mappedBy = "personDocument", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FileRecord> files = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Person getPerson() { return person; }
    public void setPerson(Person person) { this.person = person; }

    public DocumentDefinition getDocumentDefinition() { return documentDefinition; }
    public void setDocumentDefinition(DocumentDefinition documentDefinition) { this.documentDefinition = documentDefinition; }

    public PersonDocumentStatus getStatus() { return status; }
    public void setStatus(PersonDocumentStatus status) { this.status = status; }

    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public IssuingEntity getIssuerEntity() { return issuerEntity; }
    public void setIssuerEntity(IssuingEntity issuerEntity) { this.issuerEntity = issuerEntity; }

    public Long getSubmittedByEntityUserId() { return submittedByEntityUserId; }
    public void setSubmittedByEntityUserId(Long submittedByEntityUserId) { this.submittedByEntityUserId = submittedByEntityUserId; }

    public ReviewStatus getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(ReviewStatus reviewStatus) { this.reviewStatus = reviewStatus; }

    public Long getReviewedByUserId() { return reviewedByUserId; }
    public void setReviewedByUserId(Long reviewedByUserId) { this.reviewedByUserId = reviewedByUserId; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }

    public List<FileRecord> getFiles() { return files; }
    public void setFiles(List<FileRecord> files) { this.files = files; }

    /**
     * Asocia un archivo a este documento y sincroniza la referencia inversa.
     *
     * @param file archivo a asociar
     */
    public void addFile(FileRecord file) {
        files.add(file);
        file.setPersonDocument(this);
    }

    /**
     * Elimina la asociación de un archivo y limpia la referencia inversa.
     *
     * @param file archivo a desasociar
     */
    public void removeFile(FileRecord file) {
        files.remove(file);
        file.setPersonDocument(null);
    }
}
