package co.edu.unbosque.ccdigital.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidad JPA que representa una categoría del catálogo de documentos.
 *
 * <p>Se mapea a la tabla {@code categories}. Una categoría agrupa múltiples definiciones de documentos
 * ({@link DocumentDefinition}) y se utiliza para organizar el catálogo</p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
@Entity
@Table(name = "categories")
public class Category {

    /**
     * Identificador interno de la categoría (PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nombre de la categoría.
     *
     * <p>Debe ser único y no nulo. Longitud máxima: 200 caracteres.</p>
     */
    @Column(name = "name", nullable = false, unique = true, length = 200)
    private String name;

    /**
     * Slug de la categoría.
     *
     * <p>Es una versión normalizada de {@link #name} (por ejemplo, en minúsculas y con guiones).
     * Este valor se calcula en la base de datos, por lo que no se inserta ni actualiza desde JPA.</p>
     */
    @Column(name = "slug", length = 220, insertable = false, updatable = false)
    private String slug;

    /**
     * Fecha/hora de creación del registro.
     *
     * <p>Este valor lo gestiona la base de datos (default/trigger), por lo que no se inserta ni actualiza
     * desde JPA.</p>
     */
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Lista de definiciones de documentos asociadas a la categoría.
     *
     * <p>Relación uno-a-muchos con {@link DocumentDefinition}, mapeada por el atributo {@code category}
     * en la entidad hija.</p>
     *
     */
    @OneToMany(mappedBy = "category")
    private List<DocumentDefinition> documents = new ArrayList<>();

    /**
     * Retorna el id de la categoría.
     *
     * @return id de la categoría
     */
    public Long getId() {
        return id;
    }

    /**
     * Establece el id de la categoría.
     *
     * <p>Normalmente no se asigna manualmente porque es autogenerado por la base de datos.</p>
     *
     * @param id id de la categoría
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Retorna el nombre de la categoría.
     *
     * @return nombre de la categoría
     */
    public String getName() {
        return name;
    }

    /**
     * Establece el nombre de la categoría.
     *
     * @param name nombre de la categoría
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Retorna el slug de la categoría.
     *
     * @return slug de la categoría
     */
    public String getSlug() {
        return slug;
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
     * Retorna la lista de definiciones de documentos asociadas.
     *
     * @return lista de documentos/definiciones
     */
    public List<DocumentDefinition> getDocuments() {
        return documents;
    }

    /**
     * Establece la lista de definiciones de documentos asociadas.
     *
     * @param documents lista de definiciones de documentos
     */
    public void setDocuments(List<DocumentDefinition> documents) {
        this.documents = documents;
    }
}
