package co.edu.unbosque.ccdigital.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "entities")
public class IssuingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // En tu BD: e.name
    @Column(name = "name", nullable = false, length = 300)
    private String name;

    // En tu BD: e.entity_type ('EMISOR')
    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 30)
    private EntityType entityType = EntityType.EMISOR;

    // En tu BD: e.status ('APROBADA')
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private EntityStatus status = EntityStatus.APROBADA;

    // Relación con documents por entity_document_definitions
    @ManyToMany
    @JoinTable(
            name = "entity_document_definitions",
            joinColumns = @JoinColumn(name = "entity_id"),
            inverseJoinColumns = @JoinColumn(name = "document_id")
    )
    @JsonIgnore
    private List<DocumentDefinition> documentDefinitions = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public EntityType getEntityType() { return entityType; }
    public void setEntityType(EntityType entityType) { this.entityType = entityType; }

    public EntityStatus getStatus() { return status; }
    public void setStatus(EntityStatus status) { this.status = status; }

    public List<DocumentDefinition> getDocumentDefinitions() { return documentDefinitions; }
    public void setDocumentDefinitions(List<DocumentDefinition> documentDefinitions) {
        this.documentDefinitions = documentDefinitions;
    }
}
