package co.edu.unbosque.ccdigital.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Entidad emisora, persistida en la tabla {@code entities}.
 *
 * <p>Una entidad emisora puede tener permisos sobre múltiples definiciones de documentos
 * mediante la tabla de relación {@code entity_document_definitions}.</p>
 */
@Entity
@Table(name = "entities")
public class IssuingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 300)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 30)
    private EntityType entityType = EntityType.EMISOR;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private EntityStatus status = EntityStatus.APROBADA;

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
