package co.edu.unbosque.ccdigital.dto;

import co.edu.unbosque.ccdigital.entity.PersonDocumentStatus;

import java.time.LocalDate;

/**
 * DTO para crear o registrar un documento asociado a una persona a través de la API REST.
 *
 * <p>
 * Normalmente, el identificador de la persona se toma de la ruta del endpoint y se asigna en el
 * controlador antes de delegar a la capa de servicio.
 * </p>
 *
 * @author Danniel
 * @author Yeison
 * @since 2.0
 */
public class PersonDocumentRequest {

    private Long personId;
    private Long documentId;
    private PersonDocumentStatus status;
    private LocalDate issueDate;
    private LocalDate expiryDate;

    /**
     * Ruta de almacenamiento del archivo (relativa o absoluta según la estrategia del proyecto).
     */
    private String storagePath;

    /**
     * Tipo MIME del archivo (por ejemplo: {@code application/pdf}).
     */
    private String mimeType;

    /**
     * Hash SHA-256 del archivo en representación hexadecimal.
     */
    private String hashSha256;

    /**
     * Retorna el identificador de la persona.
     *
     * @return id de la persona
     */
    public Long getPersonId() {
        return personId;
    }

    /**
     * Establece el identificador de la persona.
     *
     * @param personId id de la persona
     */
    public void setPersonId(Long personId) {
        this.personId = personId;
    }

    /**
     * Retorna el identificador de la definición de documento.
     *
     * @return id de la definición del documento
     */
    public Long getDocumentId() {
        return documentId;
    }

    /**
     * Establece el identificador de la definición de documento.
     *
     * @param documentId id de la definición del documento
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
     * Retorna la ruta de almacenamiento del archivo.
     *
     * @return ruta de almacenamiento
     */
    public String getStoragePath() {
        return storagePath;
    }

    /**
     * Establece la ruta de almacenamiento del archivo.
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
     * @return hash SHA-256
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
