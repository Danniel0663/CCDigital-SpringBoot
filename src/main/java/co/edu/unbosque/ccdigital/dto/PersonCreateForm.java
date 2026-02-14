package co.edu.unbosque.ccdigital.dto;

import co.edu.unbosque.ccdigital.entity.IdType;

import java.time.LocalDate;

/**
 * DTO para capturar los datos del formulario de creación de una persona en el módulo administrativo.
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
public class PersonCreateForm {

    private IdType idType;
    private String idNumber;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
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
}
