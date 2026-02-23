package co.edu.unbosque.ccdigital.dto;

public class ForgotResetRequest extends ForgotVerifyRequest {
    private String newPassword;

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}