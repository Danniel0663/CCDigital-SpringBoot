package co.edu.unbosque.ccdigital.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidad JPA que representa una definición de documento dentro del catálogo.
 *
 * <p>
 * Se mapea a la tabla {@code documents}. Una definición describe el tipo de documento que el sistema
 * puede gestionar, incluyendo su título, entidad emisora en texto y metadatos de referencia.
 * </p>
 *
 * <p>
 * Los campos {@code created_at} y {@code updated_at} son gestionados por la base de datos.
 * </p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
@Entity
@Table(name = "documents")
public class DocumentDefinition {

    /**
     * Identificador interno de la definición (PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Categoría a la que pertenece la definición.
     *
     * <p>Columna: {@code category_id}. Carga perezosa para reducir consultas innecesarias.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /**
     * Título descriptivo del tipo de documento.
     *
     * <p>Columna: {@code title}.</p>
     */
    @Column(name = "title", nullable = false, length = 400)
    private String title;

    /**
     * Nombre de la entidad emisora en texto.
     *
     * <p>Columna: {@code issuing_entity}. Este campo es texto y no reemplaza el concepto de
     * {@link IssuingEntity}.</p>
     */
    @Column(name = "issuing_entity", nullable = false, length = 300)
    private String issuingEntity;

    /**
     * Descripción opcional del documento.
     */
    @Column(name = "description")
    private String description;

    /**
     * URL de referencia o fuente asociada.
     *
     * <p>Columna: {@code source_url}.</p>
     */
    @Column(name = "source_url", length = 600)
    private String sourceUrl;

    /**
     * Indicador de disponibilidad de la definición en el sistema.
     *
     * <p>Columna: {@code is_active}.</p>
     */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /**
     * Fecha/hora de creación del registro, gestionada por base de datos.
     */
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Fecha/hora de última actualización del registro, gestionada por base de datos.
     */
    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    /**
     * Documentos de persona creados a partir de esta definición.
     */
    @OneToMany(mappedBy = "documentDefinition", cascade = CascadeType.ALL, orphanRemoval = false)
    @JsonIgnore
    private List<PersonDocument> personDocuments = new ArrayList<>();

    /**
     * Archivos asociados a la definición (si el modelo utiliza adjuntos de catálogo).
     */
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = false)
    @JsonIgnore
    private List<FileRecord> files = new ArrayList<>();

    /**
     * Emisores autorizados para emitir esta definición.
     */
    @ManyToMany(mappedBy = "documentDefinitions")
    @JsonIgnore
    private List<IssuingEntity> issuers = new ArrayList<>();

    /**
     * Retorna el id de la definición.
     *
     * @return id de la definición
     */
    public Long getId() {
        return id;
    }

    /**
     * Establece el id de la definición.
     *
     * @param id id de la definición
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Retorna la categoría asociada.
     *
     * @return categoría
     */
    public Category getCategory() {
        return category;
    }

    /**
     * Establece la categoría asociada.
     *
     * @param category categoría
     */
    public void setCategory(Category category) {
        this.category = category;
    }

    /**
     * Retorna el título del documento.
     *
     * @return título
     */
    public String getTitle() {
        return title;
    }

    /**
     * Establece el título del documento.
     *
     * @param title título
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Retorna la entidad emisora en texto.
     *
     * @return entidad emisora
     */
    public String getIssuingEntity() {
        return issuingEntity;
    }

    /**
     * Establece la entidad emisora en texto.
     *
     * @param issuingEntity entidad emisora
     */
    public void setIssuingEntity(String issuingEntity) {
        this.issuingEntity = issuingEntity;
    }

    /**
     * Retorna la descripción del documento.
     *
     * @return descripción o {@code null}
     */
    public String getDescription() {
        return description;
    }

    /**
     * Establece la descripción del documento.
     *
     * @param description descripción
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Retorna la URL de referencia.
     *
     * @return URL de referencia o {@code null}
     */
    public String getSourceUrl() {
        return sourceUrl;
    }

    /**
     * Establece la URL de referencia.
     *
     * @param sourceUrl URL de referencia
     */
    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    /**
     * Indica si la definición está activa.
     *
     * @return {@code true} si está activa; {@code false} en caso contrario
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Establece si la definición está activa.
     *
     * @param active {@code true} para activar; {@code false} para desactivar
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Retorna la fecha/hora de creación.
     *
     * @return fecha/hora de creación
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Setter disponible por compatibilidad; se recomienda no asignar manualmente.
     *
     * @param createdAt fecha/hora de creación
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Retorna la fecha/hora de actualización.
     *
     * @return fecha/hora de actualización
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Setter disponible por compatibilidad; se recomienda no asignar manualmente.
     *
     * @param updatedAt fecha/hora de actualización
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Retorna los documentos de persona asociados a esta definición.
     *
     * @return lista de documentos de persona
     */
    public List<PersonDocument> getPersonDocuments() {
        return personDocuments;
    }

    /**
     * Establece los documentos de persona asociados.
     *
     * @param personDocuments lista de documentos de persona
     */
    public void setPersonDocuments(List<PersonDocument> personDocuments) {
        this.personDocuments = personDocuments;
    }

    /**
     * Retorna los archivos asociados a la definición.
     *
     * @return lista de archivos
     */
    public List<FileRecord> getFiles() {
        return files;
    }

    /**
     * Establece los archivos asociados a la definición.
     *
     * @param files lista de archivos
     */
    public void setFiles(List<FileRecord> files) {
        this.files = files;
    }

    /**
     * Retorna los emisores autorizados para esta definición.
     *
     * @return lista de emisores
     */
    public List<IssuingEntity> getIssuers() {
        return issuers;
    }

    /**
     * Establece los emisores autorizados para esta definición.
     *
     * @param issuers lista de emisores
     */
    public void setIssuers(List<IssuingEntity> issuers) {
        this.issuers = issuers;
    }
}
