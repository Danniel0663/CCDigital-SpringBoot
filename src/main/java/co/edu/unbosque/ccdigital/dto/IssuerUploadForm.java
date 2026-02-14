package co.edu.unbosque.ccdigital.dto;

import co.edu.unbosque.ccdigital.entity.PersonDocumentStatus;

import java.time.LocalDate;

/**
 * DTO para capturar los metadatos del formulario de carga de documentos desde el módulo del emisor (Issuer).
 *
 * <p>
 * Este DTO contiene el contexto de la persona y los metadatos del documento. El archivo físico se recibe
 * como {@code MultipartFile} y se procesa en el servicio correspondiente.
 * </p>
 *
 * @since 3.0
 */
public class IssuerUploadForm {

    /**
     * Identificador interno de la persona a la cual se asociará el documento.
     */
    private Long personId;

    /**
     * Identificador de la definición del documento seleccionada.
     */
    private Long documentId;

    /**
     * Estado funcional del documento.
     */
    private PersonDocumentStatus status = PersonDocumentStatus.VIGENTE;

    /**
     * Fecha de expedición/emisión del documento.
     */
    private LocalDate issueDate;

    /**
     * Fecha de vencimiento del documento (si aplica).
     */
    private LocalDate expiryDate;

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
     * Retorna el identificador de la definición del documento.
     *
     * @return id de la definición del documento
     */
    public Long getDocumentId() {
        return documentId;
    }

    /**
     * Establece el identificador de la definición del documento.
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
     * Retorna la fecha de expedición.
     *
     * @return fecha de expedición
     */
    public LocalDate getIssueDate() {
        return issueDate;
    }

    /**
     * Establece la fecha de expedición.
     *
     * @param issueDate fecha de expedición
     */
    public void setIssueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
    }

    /**
     * Retorna la fecha de vencimiento.
     *
     * @return fecha de vencimiento
     */
    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    /**
     * Establece la fecha de vencimiento.
     *
     * @param expiryDate fecha de vencimiento
     */
    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }
}
