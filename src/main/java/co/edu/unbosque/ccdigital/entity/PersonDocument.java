package co.edu.unbosque.ccdigital.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    // PERSONA
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    // DOCUMENTO CATÁLOGO
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

    public List<FileRecord> getFiles() { return files; }
    public void setFiles(List<FileRecord> files) { this.files = files; }

    public void addFile(FileRecord file) {
        files.add(file);
        file.setPersonDocument(this);
    }

    public void removeFile(FileRecord file) {
        files.remove(file);
        file.setPersonDocument(null);
    }
}
