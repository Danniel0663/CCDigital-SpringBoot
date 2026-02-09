package co.edu.unbosque.ccdigital.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Entidad JPA que representa un archivo almacenado en el sistema.
 *
 * <p>Se mapea a la tabla {@code files}. Un {@code FileRecord} contiene metadatos del archivo
 * (nombre original, tipo MIME, tamaño, hash SHA-256, versión, usuario que subió el archivo)</p>
 *
 * <p><b>Campos gestionados por la base de datos:</b> {@link #createdAt} está marcado como
 * {@code insertable=false, updatable=false} porque se asume que la base de datos asigna el timestamp
 * automáticamente trigger.</p>
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
     * Definición/catálogo de documento al que se asocia el archivo.
     *
     * <p>Columna: {@code document_id} (referencia a {@code documents.id}).</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private DocumentDefinition document;

    /**
     * Documento específico de una persona al que pertenece el archivo (si aplica).
     *
     * <p>Columna: {@code person_document_id} (referencia a {@code person_documents.id}).</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_document_id")
    private PersonDocument personDocument;

    /**
     * Nombre original del archivo, tal como fue cargado por el usuario (incluye extensión).
     */
    @Column(name = "original_name", nullable = false, length = 300)
    private String originalName;

    /**
     * Tipo MIME del archivo (por ejemplo: {@code application/pdf}.
     */
    @Column(name = "mime_type", nullable = false, length = 150)
    private String mimeType;

    /**
     * Tamaño del archivo en bytes.
     */
    @Column(name = "byte_size", nullable = false)
    private Long byteSize;

    /**
     * Hash SHA-256 del archivo.
     *
     * <p>Se utiliza para verificación de integridad y puede ser base para registros de auditoría
     * o sincronización con blockchain.</p>
     */
    @Column(name = "sha256_hex", nullable = false, length = 64)
    private String hashSha256;

    /**
     * Estrategia de almacenamiento del archivo (por ejemplo: en ruta o como blob).
     *
     * <p>Se persiste como texto (STRING) en la columna {@code stored_as}.</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "stored_as", nullable = false, length = 10)
    private FileStoredAs storedAs = FileStoredAs.PATH;

    /**
     * Ruta donde se almacena el archivo cuando {@link #storedAs} indica almacenamiento por ruta.
     *
     * <p>Columna: {@code file_path}. Puede ser ruta absoluta o relativa según configuración.</p>
     */
    @Column(name = "file_path", length = 800)
    private String storagePath;

    /**
     * Contenido binario del archivo cuando se almacena en base de datos (BLOB).
     *
     * <p>Columna: {@code content}. Se utiliza si {@link #storedAs} indica almacenamiento como blob.</p>
     */
    @Lob
    @Column(name = "content")
    private byte[] content;

    /**
     * Versión del archivo.
     *
     * <p>Se incrementa cuando se sube una nueva versión del mismo archivo.</p>
     */
    @Column(name = "version", nullable = false)
    private Integer version = 1;

    /**
     * Identificador del usuario que cargó el archivo (si aplica).
     *
     * <p>Columna: {@code uploaded_by_user}. Puede ser {@code null} si el origen no es un usuario autenticado.</p>
     */
    @Column(name = "uploaded_by_user")
    private Long uploadedByUserId;

    /**
     * Fecha y hora de carga del archivo (gestionada por base de datos).
     *
     * <p>Columna: {@code uploaded_at}. No se actualiza ni inserta desde JPA.</p>
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
     * <p>Normalmente no se asigna manualmente porque es autogenerado.</p>
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
     * Retorna el documento de persona asociado (si aplica).
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
     * @return hash SHA-256
     */
    public String getHashSha256() {
        return hashSha256;
    }

    /**
     * Establece el hash del archivo.
     *
     * @param hashSha256 hash SHA-256
     */
    public void setHashSha256(String hashSha256) {
        this.hashSha256 = hashSha256;
    }

    /**
     * Retorna la estrategia de almacenamiento.
     *
     * @return estrategia de almacenamiento
     */
    public FileStoredAs getStoredAs() {
        return storedAs;
    }

    /**
     * Establece la estrategia de almacenamiento.
     *
     * @param storedAs estrategia de almacenamiento
     */
    public void setStoredAs(FileStoredAs storedAs) {
        this.storedAs = storedAs;
    }

    /**
     * Retorna la ruta de almacenamiento (si aplica).
     *
     * @return ruta del archivo
     */
    public String getStoragePath() {
        return storagePath;
    }

    /**
     * Establece la ruta de almacenamiento (si aplica).
     *
     * @param storagePath ruta del archivo
     */
    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    /**
     * Retorna el contenido binario del archivo.
     *
     * @return contenido binario
     */
    public byte[] getContent() {
        return content;
    }

    /**
     * Establece el contenido binario del archivo.
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
     * Retorna el id del usuario que subió el archivo (si aplica).
     *
     * @return id del usuario o {@code null}
     */
    public Long getUploadedByUserId() {
        return uploadedByUserId;
    }

    /**
     * Establece el id del usuario que subió el archivo (si aplica).
     *
     * @param uploadedByUserId id del usuario
     */
    public void setUploadedByUserId(Long uploadedByUserId) {
        this.uploadedByUserId = uploadedByUserId;
    }

    /**
     * Retorna la fecha y hora de carga del archivo.
     *
     * @return fecha y hora de carga
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Establece la fecha y hora de carga.
     *
     * <p>Generalmente la base de datos gestiona este valor. Se recomienda no asignarlo manualmente.</p>
     *
     * @param createdAt fecha y hora de carga
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
