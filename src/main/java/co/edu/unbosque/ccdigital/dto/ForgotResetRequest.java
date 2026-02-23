package co.edu.unbosque.ccdigital.dto;

/**
 * DTO para completar la recuperación de contraseña.
 *
 * <p>Extiende {@link ForgotVerifyRequest} para reutilizar la validación de identidad básica
 * (correo + documento) y agrega:</p>
 * <ul>
 *   <li>{@code resetCode}: código temporal enviado por correo</li>
 *   <li>{@code newPassword}: nueva contraseña elegida por el usuario</li>
 * </ul>
 */
public class ForgotResetRequest extends ForgotVerifyRequest {
    private String resetCode;
    private String newPassword;

    /**
     * @return código temporal enviado al correo del usuario
     */
    public String getResetCode() { return resetCode; }

    /**
     * @param resetCode código temporal enviado al correo del usuario
     */
    public void setResetCode(String resetCode) { this.resetCode = resetCode; }

    /**
     * @return nueva contraseña en texto plano (se encripta en el servicio)
     */
    public String getNewPassword() { return newPassword; }

    /**
     * @param newPassword nueva contraseña en texto plano (se encripta en el servicio)
     */
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
