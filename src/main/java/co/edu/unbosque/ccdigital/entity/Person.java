package co.edu.unbosque.ccdigital.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidad JPA que representa una persona registrada en CCDigital.
 *
 * <p>
 * Se mapea a la tabla {@code persons} e incluye información de identificación, datos personales básicos
 * y datos de contacto.
 * </p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
@Entity
@Table(name = "persons")
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "id_type", nullable = false, length = 10)
    private IdType idType;

    @Column(name = "id_number", nullable = false, length = 40, unique = true)
    private String idNumber;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "birthdate")
    private LocalDate birthdate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "person")
    @JsonIgnore
    private List<PersonDocument> personDocuments = new ArrayList<>();

    /**
     * Retorna el id interno de la persona.
     *
     * @return id de la persona
     */
    public Long getId() {
        return id;
    }

    /**
     * Establece el id interno de la persona.
     *
     * @param id id de la persona
     */
    public void setId(Long id) {
        this.id = id;
    }

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
     * @return correo electrónico o {@code null}
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
     * @return teléfono o {@code null}
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
     * @return fecha de nacimiento o {@code null}
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
     * Retorna la fecha/hora de creación del registro.
     *
     * @return fecha/hora de creación
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Establece la fecha/hora de creación.
     *
     * @param createdAt fecha/hora de creación
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Retorna los documentos asociados a la persona.
     *
     * @return lista de documentos de persona
     */
    public List<PersonDocument> getPersonDocuments() {
        return personDocuments;
    }

    /**
     * Establece los documentos asociados a la persona.
     *
     * @param personDocuments lista de documentos de persona
     */
    public void setPersonDocuments(List<PersonDocument> personDocuments) {
        this.personDocuments = personDocuments;
    }

    /**
     * Retorna el nombre completo de la persona concatenando nombres y apellidos.
     *
     * @return nombre completo
     */
    @Transient
    public String getFullName() {
        String fn = (firstName == null) ? "" : firstName;
        String ln = (lastName == null) ? "" : lastName;
        return (fn + " " + ln).trim();
    }
}
