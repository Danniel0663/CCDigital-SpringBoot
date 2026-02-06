package co.edu.unbosque.ccdigital.dto;

import co.edu.unbosque.ccdigital.entity.PersonDocumentStatus;

import java.time.LocalDate;

public class DocumentUploadForm {
    private Long documentId;
    private PersonDocumentStatus status = PersonDocumentStatus.VIGENTE;
    private LocalDate issueDate;
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
