package co.edu.unbosque.ccdigital.dto;

import co.edu.unbosque.ccdigital.entity.PersonDocumentStatus;

import java.time.LocalDate;

/**
 * DTO utilizado para crear o registrar un documento asociado a una persona
 * a través de la API REST.
 *
 * <p>Es consumido por un controlador REST como {@code POST /api/persons/{personId}/documents}.
 * En ese caso, el {@code personId} suele venir en la ruta y se fuerza en el request para asegurar
 * consistencia (evitar depender del body para esa relación).</p>
 * 
 * @author Danniel
 * @author Yeison
 * @since 2.0
 */
public class PersonDocumentRequest {

    /**
     * Identificador interno de la persona a la que pertenece el documento.
     */
    private Long personId;

    /**
     * Identificador de la definición del documento (catálogo de documentos).
     */
    private Long documentId;

    /**
     * Estado funcional del documento (por ejemplo: vigente, vencido, etc.).
     */
    private PersonDocumentStatus status;

    /**
     * Fecha de expedición/emisión del documento.
     */
    private LocalDate issueDate;

    /**
     * Fecha de vencimiento del documento (si aplica).
     */
    private LocalDate expiryDate;

    /**
     * Ruta/ubicación donde se almacena el archivo en el sistema (relativa o absoluta,
     * según la estrategia de almacenamiento del proyecto).
     */
    private String storagePath;

    /**
     * Tipo MIME del archivo almacenado (por ejemplo: {@code application/pdf}, {@code image/png}).
     */
    private String mimeType;

    /**
     * Hash SHA-256 del archivo, usado para integridad/verificación.
     *
     * <p>Puede emplearse para comparar el archivo almacenado vs. el original o para registrar
     * evidencias de integridad como sincronización con blockchain.</p>
     */
    private String hashSha256;

    /**
     * Retorna el id de la persona asociada al documento.
     *
     * @return id de la persona
     */
    public Long getPersonId() {
        return personId;
    }

    /**
     * Establece el id de la persona asociada al documento.
     *
     * @param personId id de la persona
     */
    public void setPersonId(Long personId) {
        this.personId = personId;
    }

    /**
     * Retorna el id de la definición del documento.
     *
     * @return id de la definición de documento
     */
    public Long getDocumentId() {
        return documentId;
    }

    /**
     * Establece el id de la definición del documento.
     *
     * @param documentId id de la definición de documento
     */
    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    /**
     * Retorna el estado del documento.
     *
     * @return estado del documento
     */
    public PersonDocumentStatus getStatus() {
        return status;
    }

    /**
     * Establece el estado del documento.
     *
     * @param status estado del documento
     */
    public void setStatus(PersonDocumentStatus status) {
        this.status = status;
    }

    /**
     * Retorna la fecha de expedición del documento.
     *
     * @return fecha de expedición
     */
    public LocalDate getIssueDate() {
        return issueDate;
    }

    /**
     * Establece la fecha de expedición del documento.
     *
     * @param issueDate fecha de expedición
     */
    public void setIssueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
    }

    /**
     * Retorna la fecha de vencimiento del documento.
     *
     * @return fecha de vencimiento
     */
    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    /**
     * Establece la fecha de vencimiento del documento.
     *
     * @param expiryDate fecha de vencimiento
     */
    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    /**
     * Retorna la ruta/ubicación de almacenamiento del archivo.
     *
     * @return ruta de almacenamiento
     */
    public String getStoragePath() {
        return storagePath;
    }

    /**
     * Establece la ruta/ubicación de almacenamiento del archivo.
     *
     * @param storagePath ruta de almacenamiento
     */
    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
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
}
