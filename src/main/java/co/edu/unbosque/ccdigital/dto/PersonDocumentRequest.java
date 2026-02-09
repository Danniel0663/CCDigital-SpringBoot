package co.edu.unbosque.ccdigital.dto;

import co.edu.unbosque.ccdigital.entity.PersonDocumentStatus;

import java.time.LocalDate;

/**
 * DTO utilizado por la API para crear un registro de documento asociado a una persona.
 *
 * <p>Incluye metadatos del documento y campos de soporte para almacenamiento/huella
 * (ruta, tipo MIME y hash SHA-256) cuando aplique.</p>
 */
public class PersonDocumentRequest {

    private Long personId;
    private Long documentId;
    private PersonDocumentStatus status;
    private LocalDate issueDate;
    private LocalDate expiryDate;

    /**
     * Ruta o referencia de almacenamiento del archivo (cuando se almacena fuera de la BD).
     */
    private String storagePath;

    /**
     * Tipo MIME del archivo asociado.
     */
    private String mimeType;

    /**
     * Hash SHA-256 en formato hexadecimal.
     */
    private String hashSha256;

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

    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public String getHashSha256() { return hashSha256; }
    public void setHashSha256(String hashSha256) { this.hashSha256 = hashSha256; }
}
