package co.edu.unbosque.ccdigital.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "documents")
public class DocumentDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "title", nullable = false, length = 400)
    private String title;

    @Column(name = "issuing_entity", nullable = false, length = 300)
    private String issuingEntity;

    @Column(name = "description")
    private String description;

    @Column(name = "source_url", length = 600)
    private String sourceUrl;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    // OJO: mappedBy debe coincidir con el nombre del atributo en PersonDocument
    @OneToMany(mappedBy = "documentDefinition", cascade = CascadeType.ALL, orphanRemoval = false)
    @JsonIgnore
    private List<PersonDocument> personDocuments = new ArrayList<>();

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = false)
    @JsonIgnore
    private List<FileRecord> files = new ArrayList<>();
    @ManyToMany(mappedBy = "documentDefinitions")
    @JsonIgnore
    private List<IssuingEntity> issuers = new java.util.ArrayList<>();

    public List<IssuingEntity> getIssuers() { return issuers; }
    public void setIssuers(List<IssuingEntity> issuers) { this.issuers = issuers; }

    public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}
	public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getIssuingEntity() { return issuingEntity; }
    public void setIssuingEntity(String issuingEntity) { this.issuingEntity = issuingEntity; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public List<PersonDocument> getPersonDocuments() { return personDocuments; }
    public void setPersonDocuments(List<PersonDocument> personDocuments) { this.personDocuments = personDocuments; }

    public List<FileRecord> getFiles() { return files; }
    public void setFiles(List<FileRecord> files) { this.files = files; }
}
