package co.edu.unbosque.ccdigital.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidad JPA que representa una definición (catálogo) de documento dentro de CCDigital.
 *
 * <p>Se mapea a la tabla {@code documents}. Un {@code DocumentDefinition} describe el tipo de documento
 * que puede existir en el sistema</p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
@Entity
@Table(name = "documents")
public class DocumentDefinition {

    /**
     * Identificador interno de la definición de documento (PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Categoría a la que pertenece esta definición de documento.
     *
     * <p>Relación Many-to-One. Se carga de forma perezosa para evitar traer la categoría
     * automáticamente en todos los escenarios.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /**
     * Título del documento (nombre descriptivo del tipo de documento).
     */
    @Column(name = "title", nullable = false, length = 400)
    private String title;

    /**
     * Nombre (texto) de la entidad emisora del documento.
     *
     * <p>Este campo es el "issuing entity" en texto, diferente del concepto de {@link IssuingEntity}
     * que representa emisores aprobados y registrados.</p>
     */
    @Column(name = "issuing_entity", nullable = false, length = 300)
    private String issuingEntity;

    /**
     * Descripción opcional del documento.
     */
    @Column(name = "description")
    private String description;

    /**
     * URL de referencia o fuente del documento (si aplica).
     */
    @Column(name = "source_url", length = 600)
    private String sourceUrl;

    /**
     * Bandera que indica si la definición está activa y disponible para uso en el sistema.
     */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /**
     * Fecha/hora de creación del registro (gestionada por la base de datos).
     */
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Fecha/hora de última actualización del registro (gestionada por la base de datos).
     */
    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    /**
     * Lista de documentos de persona ({@link PersonDocument}) creados a partir de esta definición.
     *
     * <p><b>Importante:</b> El valor de {@code mappedBy} debe coincidir con el nombre del atributo
     * en {@link PersonDocument} que referencia a {@code DocumentDefinition}.</p>
     *
     */
    @OneToMany(mappedBy = "documentDefinition", cascade = CascadeType.ALL, orphanRemoval = false)
    @JsonIgnore
    private List<PersonDocument> personDocuments = new ArrayList<>();

    /**
     * Archivos asociados a esta definición de documento (si el modelo los usa para adjuntos del catálogo).
     *
     * <p>Se marca con {@link JsonIgnore} para evitar cargas y ciclos al serializar.</p>
     */
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = false)
    @JsonIgnore
    private List<FileRecord> files = new ArrayList<>();

    /**
     * Emisores aprobados que pueden emitir esta definición de documento.
     *
     * <p>Relación Many-to-Many inversa, mapeada por el atributo {@code documentDefinitions}
     * en {@link IssuingEntity}.</p>
     *
     */
    @ManyToMany(mappedBy = "documentDefinitions")
    @JsonIgnore
    private List<IssuingEntity> issuers = new ArrayList<>();

    /**
     * Retorna la lista de emisores asociados.
     *
     * @return lista de emisores
     */
    public List<IssuingEntity> getIssuers() {
        return issuers;
    }

    /**
     * Establece la lista de emisores asociados.
     *
     * @param issuers lista de emisores
     */
    public void setIssuers(List<IssuingEntity> issuers) {
        this.issuers = issuers;
    }

    /**
     * Establece la fecha de creación.
     *
     * <p>Generalmente este campo lo gestiona la base de datos. Este setter existe por compatibilidad
     * con frameworks y serialización, pero se recomienda no asignarlo manualmente en la lógica de negocio.</p>
     *
     * @param createdAt fecha de creación
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Establece la fecha de actualización.
     *
     * <p>Este campo lo gestiona la base de datos. Este setter existe por compatibilidad
     * con frameworks/serialización, pero se recomienda no asignarlo manualmente en la lógica de negocio.</p>
     *
     * @param updatedAt fecha de actualización
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

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
     * <p>Normalmente no se asigna manualmente porque es autogenerado.</p>
     *
     * @param id id de la definición
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Retorna la categoría asociada.
     *
     * @return categoría del documento
     */
    public Category getCategory() {
        return category;
    }

    /**
     * Establece la categoría asociada.
     *
     * @param category categoría del documento
     */
    public void setCategory(Category category) {
        this.category = category;
    }

    /**
     * Retorna el título del documento.
     *
     * @return título del documento
     */
    public String getTitle() {
        return title;
    }

    /**
     * Establece el título del documento.
     *
     * @param title título del documento
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Retorna el nombre (texto) de la entidad emisora.
     *
     * @return entidad emisora (texto)
     */
    public String getIssuingEntity() {
        return issuingEntity;
    }

    /**
     * Establece el nombre (texto) de la entidad emisora.
     *
     * @param issuingEntity entidad emisora (texto)
     */
    public void setIssuingEntity(String issuingEntity) {
        this.issuingEntity = issuingEntity;
    }

    /**
     * Retorna la descripción del documento (opcional).
     *
     * @return descripción
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
     * Retorna la URL de referencia del documento.
     *
     * @return URL de referencia
     */
    public String getSourceUrl() {
        return sourceUrl;
    }

    /**
     * Establece la URL de referencia del documento.
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
     * Retorna la fecha de creación del registro.
     *
     * @return fecha de creación
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Retorna la fecha de actualización del registro.
     *
     * @return fecha de actualización
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Retorna la lista de documentos de persona asociados a esta definición.
     *
     * @return lista de documentos de persona
     */
    public List<PersonDocument> getPersonDocuments() {
        return personDocuments;
    }

    /**
     * Establece la lista de documentos de persona asociados a esta definición.
     *
     * @param personDocuments lista de documentos de persona
     */
    public void setPersonDocuments(List<PersonDocument> personDocuments) {
        this.personDocuments = personDocuments;
    }

    /**
     * Retorna la lista de archivos asociados a esta definición.
     *
     * @return lista de archivos
     */
    public List<FileRecord> getFiles() {
        return files;
    }

    /**
     * Establece la lista de archivos asociados a esta definición.
     *
     * @param files lista de archivos
     */
    public void setFiles(List<FileRecord> files) {
        this.files = files;
    }
}
