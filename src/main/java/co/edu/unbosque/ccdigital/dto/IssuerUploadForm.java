package co.edu.unbosque.ccdigital.dto;

import co.edu.unbosque.ccdigital.entity.PersonDocumentStatus;

import java.time.LocalDate;

/**
 * DTO utilizado para capturar los datos del formulario de carga de documentos en el módulo de Emisores (Issuer).
 *
 * <p><b>Valor por defecto:</b> {@link #status} se inicializa como {@link PersonDocumentStatus#VIGENTE}.</p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
public class IssuerUploadForm {

    /**
     * Identificador del emisor (issuer) que realiza la carga del documento.
     */
    private Long issuerId;

    /**
     * Identificador de la persona a la cual se asociará el documento cargado.
     */
    private Long personId;

    /**
     * Identificador de la definición de documento seleccionada (catálogo).
     */
    private Long documentId;

    /**
     * Estado funcional del documento.
     *
     * <p>Por defecto se asigna {@link PersonDocumentStatus#VIGENTE}.</p>
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
     * Retorna el id del emisor que realiza la carga.
     *
     * @return id del emisor
     */
    public Long getIssuerId() {
        return issuerId;
    }

    /**
     * Establece el id del emisor que realiza la carga.
     *
     * @param issuerId id del emisor
     */
    public void setIssuerId(Long issuerId) {
        this.issuerId = issuerId;
    }

    /**
     * Retorna el id de la persona asociada a la carga.
     *
     * @return id de la persona
     */
    public Long getPersonId() {
        return personId;
    }

    /**
     * Establece el id de la persona asociada a la carga.
     *
     * @param personId id de la persona
     */
    public void setPersonId(Long personId) {
        this.personId = personId;
    }

    /**
     * Retorna el id de la definición de documento seleccionada.
     *
     * @return id de la definición de documento
     */
    public Long getDocumentId() {
        return documentId;
    }

    /**
     * Establece el id de la definición de documento seleccionada.
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
}
