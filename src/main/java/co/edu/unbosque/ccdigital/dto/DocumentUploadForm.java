package co.edu.unbosque.ccdigital.dto;

import co.edu.unbosque.ccdigital.entity.PersonDocumentStatus;

import java.time.LocalDate;

/**
 * DTO para carga de documentos desde el módulo administrativo.
 *
 * <p>Contiene el identificador del tipo de documento y los metadatos asociados
 * (estado, fechas de emisión y vencimiento).</p>
 */
public class DocumentUploadForm {

    /**
     * Identificador de la definición del documento a asociar.
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

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    public PersonDocumentStatus getStatus() { return status; }
    public void setStatus(PersonDocumentStatus status) { this.status = status; }

    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
}
