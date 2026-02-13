package co.edu.unbosque.ccdigital.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidad JPA que representa una categoría del catálogo de documentos.
 *
 * <p>
 * Se mapea a la tabla {@code categories}. Una categoría agrupa múltiples definiciones de documentos
 * ({@link DocumentDefinition}) y se utiliza para organizar el catálogo.
 * </p>
 *
 * <p>
 * El campo {@code slug} es calculado por la base de datos; por lo tanto, está marcado como
 * {@code insertable=false, updatable=false}.
 * </p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
@Entity
@Table(name = "categories")
public class Category {

    /**
     * Identificador interno de la categoría.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nombre de la categoría.
     *
     * <p>Columna: {@code name}. Único y no nulo.</p>
     */
    @Column(name = "name", nullable = false, unique = true, length = 200)
    private String name;

    /**
     * Slug calculado por la base de datos a partir de {@link #name}.
     *
     * <p>Columna: {@code slug}. No se escribe desde JPA.</p>
     */
    @Column(name = "slug", length = 220, insertable = false, updatable = false)
    private String slug;

    /**
     * Fecha/hora de creación del registro gestionada por base de datos.
     *
     * <p>Columna: {@code created_at}. No se escribe desde JPA.</p>
     */
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Definiciones de documentos asociadas a la categoría.
     *
     * <p>Relación uno-a-muchos, mapeada por {@code category} en {@link DocumentDefinition}.</p>
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
     * Retorna el slug calculado por base de datos.
     *
     * @return slug
     */
    public String getSlug() {
        return slug;
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
     * Retorna las definiciones de documentos asociadas.
     *
     * @return lista de definiciones
     */
    public List<DocumentDefinition> getDocuments() {
        return documents;
    }

    /**
     * Establece las definiciones de documentos asociadas.
     *
     * @param documents lista de definiciones
     */
    public void setDocuments(List<DocumentDefinition> documents) {
        this.documents = documents;
    }
}
