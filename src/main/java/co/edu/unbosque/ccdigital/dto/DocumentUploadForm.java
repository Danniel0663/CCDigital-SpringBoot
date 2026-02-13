package co.edu.unbosque.ccdigital.dto;

import co.edu.unbosque.ccdigital.entity.PersonDocumentStatus;

import java.time.LocalDate;

/**
 * DTO para capturar los metadatos del formulario de carga de documentos asociados a una persona.
 *
 * <p>
 * Este DTO representa únicamente los metadatos del documento. El archivo físico se recibe en la misma
 * petición como {@code MultipartFile} y se procesa en la capa de servicio.
 * </p>
 *
 * <p>
 * El estado por defecto es {@link PersonDocumentStatus#VIGENTE}.
 * </p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
public class DocumentUploadForm {

    /**
     * Identificador de la definición del documento seleccionada en el catálogo.
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
     * Retorna el identificador de la definición de documento seleccionada.
     *
     * @return id de la definición de documento
     */
    public Long getDocumentId() {
        return documentId;
    }

    /**
     * Establece el identificador de la definición de documento seleccionada.
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
