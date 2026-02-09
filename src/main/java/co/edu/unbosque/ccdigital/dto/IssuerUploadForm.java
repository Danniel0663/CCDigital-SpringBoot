package co.edu.unbosque.ccdigital.dto;

import co.edu.unbosque.ccdigital.entity.PersonDocumentStatus;

import java.time.LocalDate;

/**
 * DTO para carga de documentos por parte de un emisor.
 *
 * <p>Incluye el identificador de la persona, el tipo de documento autorizado
 * para el emisor y los metadatos del documento.</p>
 */
public class IssuerUploadForm {

    /**
     * Identificador de la persona a la que se asociará el documento.
     */
    private Long personId;

    /**
     * Identificador de la definición del documento a cargar.
     */
    private Long documentId;

    /**
     * Estado del documento (por defecto: VIGENTE).
     */
    private PersonDocumentStatus status = PersonDocumentStatus.VIGENTE;

    /**
     * Fecha de emisión del documento.
     */
    private LocalDate issueDate;

    /**
     * Fecha de vencimiento del documento.
     */
    private LocalDate expiryDate;

    public Long getPersonId() { return personId; }
    public void setPersonId(Long personId) { this.personId = personId; }

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    public PersonDocumentStatus getStatus() { return status; }
    public void setStatus(PersonDocumentStatus status) { this.status = status; }

    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
}
