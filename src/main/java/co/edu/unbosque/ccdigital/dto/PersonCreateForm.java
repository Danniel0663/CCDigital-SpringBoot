package co.edu.unbosque.ccdigital.dto;

import co.edu.unbosque.ccdigital.entity.IdType;

import java.time.LocalDate;

/**
 * DTO utilizado para capturar los datos del formulario de creación
 * de una persona en la interfaz web (módulo administrativo).
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
public class PersonCreateForm {

    /**
     * Tipo de identificación de la persona (por ejemplo: CC, TI, CE, PAS, etc.).
     */
    private IdType idType;

    /**
     * Número de identificación de la persona.
     */
    private String idNumber;

    /**
     * Nombres de la persona.
     */
    private String firstName;

    /**
     * Apellidos de la persona.
     */
    private String lastName;

    /**
     * Correo electrónico de contacto.
     */
    private String email;

    /**
     * Teléfono de contacto.
     */
    private String phone;

    /**
     * Fecha de nacimiento de la persona.
     */
    private LocalDate birthdate;

    /**
     * Retorna el tipo de identificación.
     *
     * @return tipo de identificación
     */
    public IdType getIdType() {
        return idType;
    }

    /**
     * Establece el tipo de identificación.
     *
     * @param idType tipo de identificación
     */
    public void setIdType(IdType idType) {
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
     * Retorna los nombres de la persona.
     *
     * @return nombres
     */
    public String getFirstName() {
        return firstName;
    }

    /**
     * Establece los nombres de la persona.
     *
     * @param firstName nombres
     */
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    /**
     * Retorna los apellidos de la persona.
     *
     * @return apellidos
     */
    public String getLastName() {
        return lastName;
    }

    /**
     * Establece los apellidos de la persona.
     *
     * @param lastName apellidos
     */
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    /**
     * Retorna el correo electrónico de la persona.
     *
     * @return correo electrónico
     */
    public String getEmail() {
        return email;
    }

    /**
     * Establece el correo electrónico de la persona.
     *
     * @param email correo electrónico
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Retorna el teléfono de la persona.
     *
     * @return teléfono
     */
    public String getPhone() {
        return phone;
    }

    /**
     * Establece el teléfono de la persona.
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
}
