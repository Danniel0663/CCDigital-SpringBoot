package co.edu.unbosque.ccdigital.dto;

public class ForgotVerifyRequest {
    private String email;
    private String idType;
    private String idNumber;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getIdType() { return idType; }
    public void setIdType(String idType) { this.idType = idType; }

    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String idNumber) { this.idNumber = idNumber; }
}