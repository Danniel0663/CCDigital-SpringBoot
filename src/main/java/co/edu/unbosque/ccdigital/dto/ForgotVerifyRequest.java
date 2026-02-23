package co.edu.unbosque.ccdigital.dto;

/**
 * DTO para iniciar la recuperación de contraseña del usuario final.
 *
 * <p>Representa los datos mínimos que el usuario debe ingresar para solicitar un código
 * temporal de restablecimiento por correo:</p>
 * <ul>
 *   <li>Correo del usuario</li>
 *   <li>Tipo de identificación</li>
 *   <li>Número de identificación</li>
 * </ul>
 */
public class ForgotVerifyRequest {
    private String email;
    private String idType;
    private String idNumber;

    /**
     * @return correo ingresado por el usuario
     */
    public String getEmail() { return email; }

    /**
     * @param email correo ingresado por el usuario
     */
    public void setEmail(String email) { this.email = email; }

    /**
     * @return tipo de identificación (ej. CC, CE, TI)
     */
    public String getIdType() { return idType; }

    /**
     * @param idType tipo de identificación (ej. CC, CE, TI)
     */
    public void setIdType(String idType) { this.idType = idType; }

    /**
     * @return número de identificación del usuario
     */
    public String getIdNumber() { return idNumber; }

    /**
     * @param idNumber número de identificación del usuario
     */
    public void setIdNumber(String idNumber) { this.idNumber = idNumber; }
}
