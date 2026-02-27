package co.edu.unbosque.ccdigital.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

/**
 * Entidad JPA que representa un archivo almacenado y sus metadatos.
 *
 * <p>
 * Se mapea a la tabla {@code files}. Un {@code FileRecord} almacena información del archivo (nombre original,
 * tipo MIME, tamaño, hash SHA-256, versión) y la estrategia de persistencia del contenido (ruta, blob o S3).
 * </p>
 *
 * <p>
 * El campo {@code uploaded_at} es gestionado por la base de datos; por lo tanto, se marca como
 * {@code insertable=false, updatable=false}.
 * </p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
@Entity
@Table(name = "files")
public class FileRecord {

    /**
     * Identificador interno del archivo (PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Definición/catálogo de documento asociada al archivo.
     *
     * <p>Columna: {@code document_id} (FK a {@code documents.id}).</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    @JsonIgnore
    private DocumentDefinition document;

    /**
     * Documento específico de persona al que pertenece el archivo (si aplica).
     *
     * <p>Columna: {@code person_document_id} (FK a {@code person_documents.id}).</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_document_id")
    @JsonIgnore
    private PersonDocument personDocument;

    /**
     * Nombre original del archivo (incluye extensión).
     */
    @Column(name = "original_name", nullable = false, length = 300)
    private String originalName;

    /**
     * Tipo MIME del archivo.
     */
    @Column(name = "mime_type", nullable = false, length = 150)
    private String mimeType;

    /**
     * Tamaño del archivo en bytes.
     */
    @Column(name = "byte_size", nullable = false)
    private Long byteSize;

    /**
     * Hash SHA-256 del archivo (hexadecimal).
     */
    @Column(name = "sha256_hex", nullable = false, length = 64)
    private String hashSha256;

    /**
     * Estrategia de almacenamiento del contenido del archivo.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "stored_as", nullable = false, length = 10)
    private FileStoredAs storedAs = FileStoredAs.PATH;

    /**
     * Ruta del archivo cuando el contenido se almacena como PATH.
     */
    @Column(name = "file_path", length = 800)
    private String storagePath;

    /**
     * Contenido binario del archivo cuando se almacena como BLOB.
     */
    @Lob
    @Column(name = "content")
    @JsonIgnore
    private byte[] content;

    /**
     * Versión del archivo.
     */
    @Column(name = "version", nullable = false)
    private Integer version = 1;

    /**
     * Identificador del usuario que cargó el archivo (si aplica).
     */
    @Column(name = "uploaded_by_user")
    private Long uploadedByUserId;

    /**
     * Fecha/hora de carga del archivo (gestionada por base de datos).
     *
     * <p>Columna: {@code uploaded_at}.</p>
     */
    @Column(name = "uploaded_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    /**
     * Retorna el id del archivo.
     *
     * @return id del archivo
     */
    public Long getId() {
        return id;
    }

    /**
     * Establece el id del archivo.
     *
     * @param id id del archivo
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Retorna la definición de documento asociada.
     *
     * @return definición de documento
     */
    public DocumentDefinition getDocument() {
        return document;
    }

    /**
     * Establece la definición de documento asociada.
     *
     * @param document definición de documento
     */
    public void setDocument(DocumentDefinition document) {
        this.document = document;
    }

    /**
     * Retorna el documento de persona asociado.
     *
     * @return documento de persona o {@code null}
     */
    public PersonDocument getPersonDocument() {
        return personDocument;
    }

    /**
     * Establece el documento de persona asociado.
     *
     * @param personDocument documento de persona
     */
    public void setPersonDocument(PersonDocument personDocument) {
        this.personDocument = personDocument;
    }

    /**
     * Retorna el nombre original del archivo.
     *
     * @return nombre original
     */
    public String getOriginalName() {
        return originalName;
    }

    /**
     * Establece el nombre original del archivo.
     *
     * @param originalName nombre original
     */
    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    /**
     * Retorna el tipo MIME del archivo.
     *
     * @return tipo MIME
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * Establece el tipo MIME del archivo.
     *
     * @param mimeType tipo MIME
     */
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    /**
     * Retorna el tamaño del archivo en bytes.
     *
     * @return tamaño en bytes
     */
    public Long getByteSize() {
        return byteSize;
    }

    /**
     * Establece el tamaño del archivo en bytes.
     *
     * @param byteSize tamaño en bytes
     */
    public void setByteSize(Long byteSize) {
        this.byteSize = byteSize;
    }

    /**
     * Retorna el hash SHA-256 del archivo.
     *
     * @return hash SHA-256 (hex)
     */
    public String getHashSha256() {
        return hashSha256;
    }

    /**
     * Establece el hash SHA-256 del archivo.
     *
     * @param hashSha256 hash SHA-256
     */
    public void setHashSha256(String hashSha256) {
        this.hashSha256 = hashSha256;
    }

    /**
     * Retorna la estrategia de almacenamiento del contenido.
     *
     * @return estrategia de almacenamiento
     */
    public FileStoredAs getStoredAs() {
        return storedAs;
    }

    /**
     * Establece la estrategia de almacenamiento del contenido.
     *
     * @param storedAs estrategia de almacenamiento
     */
    public void setStoredAs(FileStoredAs storedAs) {
        this.storedAs = storedAs;
    }

    /**
     * Retorna la ruta de almacenamiento (cuando aplica).
     *
     * @return ruta del archivo o {@code null}
     */
    public String getStoragePath() {
        return storagePath;
    }

    /**
     * Establece la ruta de almacenamiento (cuando aplica).
     *
     * @param storagePath ruta del archivo
     */
    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    /**
     * Retorna el contenido binario del archivo (cuando aplica).
     *
     * @return contenido binario o {@code null}
     */
    public byte[] getContent() {
        return content;
    }

    /**
     * Establece el contenido binario del archivo (cuando aplica).
     *
     * @param content contenido binario
     */
    public void setContent(byte[] content) {
        this.content = content;
    }

    /**
     * Retorna la versión del archivo.
     *
     * @return versión
     */
    public Integer getVersion() {
        return version;
    }

    /**
     * Establece la versión del archivo.
     *
     * @param version versión
     */
    public void setVersion(Integer version) {
        this.version = version;
    }

    /**
     * Retorna el id del usuario que cargó el archivo (si aplica).
     *
     * @return id del usuario o {@code null}
     */
    public Long getUploadedByUserId() {
        return uploadedByUserId;
    }

    /**
     * Establece el id del usuario que cargó el archivo (si aplica).
     *
     * @param uploadedByUserId id del usuario
     */
    public void setUploadedByUserId(Long uploadedByUserId) {
        this.uploadedByUserId = uploadedByUserId;
    }

    /**
     * Retorna la fecha/hora de carga del archivo.
     *
     * @return fecha/hora de carga
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Setter disponible por compatibilidad; se recomienda no asignar manualmente.
     *
     * @param createdAt fecha/hora de carga
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
