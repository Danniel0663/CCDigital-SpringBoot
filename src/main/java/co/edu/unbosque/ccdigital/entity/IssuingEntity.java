package co.edu.unbosque.ccdigital.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Entidad JPA que representa una entidad registrada en el sistema con capacidad de operar como emisor.
 *
 * <p>Se mapea a la tabla {@code entities}. En el contexto de CCDigital, una {@code IssuingEntity}
 * típicamente representa una organización (empresa/institución) que puede emitir documentos o credenciales.</p>
 *
 * <p><b>Defaults:</b> Por defecto, la entidad se inicializa como {@link EntityType#EMISOR} y estado
 * {@link EntityStatus#APROBADA}. Si el flujo real del negocio exige aprobación manual, estos valores
 * pueden ajustarse en la capa de servicio.</p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
@Entity
@Table(name = "entities")
public class IssuingEntity {

    /**
     * Identificador interno de la entidad (PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nombre de la entidad.
     *
     * <p>Columna: {@code name}. No nulo. Longitud máxima: 300 caracteres.</p>
     */
    @Column(name = "name", nullable = false, length = 300)
    private String name;

    /**
     * Tipo de entidad dentro del sistema.
     *
     * <p>Columna: {@code entity_type}. Se persiste como texto (STRING).</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 30)
    private EntityType entityType = EntityType.EMISOR;

    /**
     * Estado de aprobación de la entidad.
     *
     * <p>Columna: {@code status}. Se persiste como texto (STRING).</p>
     * <p>Por defecto: {@link EntityStatus#APROBADA}.</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private EntityStatus status = EntityStatus.APROBADA;

    /**
     * Definiciones de documentos que esta entidad está autorizada a emitir.
     *
     * <p>Relación Many-to-Many mediante la tabla puente {@code entity_document_definitions}:</p>
     *
     * <p>Se marca con {@link JsonIgnore} para evitar ciclos al serializar.</p>
     */
    @ManyToMany
    @JoinTable(
            name = "entity_document_definitions",
            joinColumns = @JoinColumn(name = "entity_id"),
            inverseJoinColumns = @JoinColumn(name = "document_id")
    )
    @JsonIgnore
    private List<DocumentDefinition> documentDefinitions = new ArrayList<>();

    /**
     * Retorna el id de la entidad.
     *
     * @return id de la entidad
     */
    public Long getId() {
        return id;
    }

    /**
     * Establece el id de la entidad.
     *
     * <p>Normalmente no se asigna manualmente porque es autogenerado.</p>
     *
     * @param id id de la entidad
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Retorna el nombre de la entidad.
     *
     * @return nombre de la entidad
     */
    public String getName() {
        return name;
    }

    /**
     * Establece el nombre de la entidad.
     *
     * @param name nombre de la entidad
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Retorna el tipo de entidad.
     *
     * @return tipo de entidad
     */
    public EntityType getEntityType() {
        return entityType;
    }

    /**
     * Establece el tipo de entidad.
     *
     * @param entityType tipo de entidad
     */
    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }

    /**
     * Retorna el estado de aprobación.
     *
     * @return estado de aprobación
     */
    public EntityStatus getStatus() {
        return status;
    }

    /**
     * Establece el estado de aprobación.
     *
     * @param status estado de aprobación
     */
    public void setStatus(EntityStatus status) {
        this.status = status;
    }

    /**
     * Retorna la lista de definiciones de documentos autorizadas.
     *
     * @return lista de definiciones de documentos
     */
    public List<DocumentDefinition> getDocumentDefinitions() {
        return documentDefinitions;
    }

    /**
     * Establece la lista de definiciones de documentos autorizadas.
     *
     * @param documentDefinitions lista de definiciones de documentos
     */
    public void setDocumentDefinitions(List<DocumentDefinition> documentDefinitions) {
        this.documentDefinitions = documentDefinitions;
    }
}
