package co.edu.unbosque.ccdigital.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 200)
    private String name;

    @Column(name = "slug", length = 220, insertable = false, updatable = false)
    private String slug;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "category")
    private List<DocumentDefinition> documents = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public List<DocumentDefinition> getDocuments() { return documents; }
    public void setDocuments(List<DocumentDefinition> documents) { this.documents = documents; }
}
