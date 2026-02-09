package co.edu.unbosque.ccdigital.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Archivo asociado a una definición de documento y opcionalmente a un documento cargado por persona.
 *
 * <p>Permite almacenamiento por ruta (PATH), contenido binario (BLOB) o mecanismo externo (S3).</p>
 */
@Entity
@Table(name = "files")
public class FileRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Definición de documento (catálogo) a la que pertenece el archivo.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private DocumentDefinition document;

    /**
     * Documento específico de una persona. Puede ser nulo cuando el archivo no esté ligado a una carga individual.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_document_id")
    private PersonDocument personDocument;

    @Column(name = "original_name", nullable = false, length = 300)
    private String originalName;

    @Column(name = "mime_type", nullable = false, length = 150)
    private String mimeType;

    @Column(name = "byte_size", nullable = false)
    private Long byteSize;

    @Column(name = "sha256_hex", nullable = false, length = 64)
    private String hashSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "stored_as", nullable = false, length = 10)
    private FileStoredAs storedAs = FileStoredAs.PATH;

    @Column(name = "file_path", length = 800)
    private String storagePath;

    @Lob
    @Column(name = "content")
    private byte[] content;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    /**
     * Identificador del usuario administrativo que cargó el archivo, cuando aplique.
     */
    @Column(name = "uploaded_by_user")
    private Long uploadedByUserId;

    /**
     * Fecha de carga gestionada por la base de datos.
     */
    @Column(name = "uploaded_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public DocumentDefinition getDocument() { return document; }
    public void setDocument(DocumentDefinition document) { this.document = document; }

    public PersonDocument getPersonDocument() { return personDocument; }
    public void setPersonDocument(PersonDocument personDocument) { this.personDocument = personDocument; }

    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public Long getByteSize() { return byteSize; }
    public void setByteSize(Long byteSize) { this.byteSize = byteSize; }

    public String getHashSha256() { return hashSha256; }
    public void setHashSha256(String hashSha256) { this.hashSha256 = hashSha256; }

    public FileStoredAs getStoredAs() { return storedAs; }
    public void setStoredAs(FileStoredAs storedAs) { this.storedAs = storedAs; }

    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }

    public byte[] getContent() { return content; }
    public void setContent(byte[] content) { this.content = content; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public Long getUploadedByUserId() { return uploadedByUserId; }
    public void setUploadedByUserId(Long uploadedByUserId) { this.uploadedByUserId = uploadedByUserId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
