package co.edu.unbosque.ccdigital.dto;

import java.time.LocalDate;

/**
 * DTO para captura del formulario de registro de usuario final.
 *
 * <p>
 * Encapsula todos los datos capturados en la vista de registro para:
 * </p>
 * <ul>
 *   <li>Validar identidad de una persona existente en {@code persons}.</li>
 *   <li>Actualizar su información personal/contacto en base de datos.</li>
 *   <li>Crear su cuenta de acceso en {@code users} con contraseña en hash BCrypt.</li>
 * </ul>
 *
 * <p>
 * El campo {@code idNumber} se usa como llave de enlace con la persona ya registrada.
 * </p>
 *
 * @since 3.0
 */
public class UserRegisterForm {

    private String idType;
    private String idNumber;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private LocalDate birthdate;
    private String password;
    private String confirmPassword;

    /**
     * Retorna el tipo de identificación.
     *
     * @return tipo de identificación
     */
    public String getIdType() {
        return idType;
    }

    /**
     * Establece el tipo de identificación.
     *
     * @param idType tipo de identificación
     */
    public void setIdType(String idType) {
        this.idType = idType;
    }

    /**
     * Retorna el número de identificación.
     *
     * @return número de identificación
     */
    public String getIdNumber() {
        return idNumber;
    }

    /**
     * Establece el número de identificación.
     *
     * @param idNumber número de identificación
     */
    public void setIdNumber(String idNumber) {
        this.idNumber = idNumber;
    }

    /**
     * Retorna los nombres.
     *
     * @return nombres
     */
    public String getFirstName() {
        return firstName;
    }

    /**
     * Establece los nombres.
     *
     * @param firstName nombres
     */
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    /**
     * Retorna los apellidos.
     *
     * @return apellidos
     */
    public String getLastName() {
        return lastName;
    }

    /**
     * Establece los apellidos.
     *
     * @param lastName apellidos
     */
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    /**
     * Retorna el correo electrónico.
     *
     * @return correo electrónico
     */
    public String getEmail() {
        return email;
    }

    /**
     * Establece el correo electrónico.
     *
     * @param email correo electrónico
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Retorna el teléfono.
     *
     * @return teléfono
     */
    public String getPhone() {
        return phone;
    }

    /**
     * Establece el teléfono.
     *
     * @param phone teléfono
     */
    public void setPhone(String phone) {
        this.phone = phone;
    }

    /**
     * Retorna la fecha de nacimiento.
     *
     * @return fecha de nacimiento
     */
    public LocalDate getBirthdate() {
        return birthdate;
    }

    /**
     * Establece la fecha de nacimiento.
     *
     * @param birthdate fecha de nacimiento
     */
    public void setBirthdate(LocalDate birthdate) {
        this.birthdate = birthdate;
    }

    /**
     * Retorna la contraseña en texto plano.
     *
     * @return contraseña
     */
    public String getPassword() {
        return password;
    }

    /**
     * Establece la contraseña en texto plano.
     *
     * @param password contraseña
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Retorna la confirmación de contraseña.
     *
     * @return confirmación de contraseña
     */
    public String getConfirmPassword() {
        return confirmPassword;
    }

    /**
     * Establece la confirmación de contraseña.
     *
     * @param confirmPassword confirmación de contraseña
     */
    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
