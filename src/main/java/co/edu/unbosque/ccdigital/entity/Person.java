package co.edu.unbosque.ccdigital.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidad JPA que representa una persona registrada en el sistema CCDigital.
 *
 * <p>Se mapea a la tabla {@code persons}. Esta entidad almacena información de identificación,
 * datos personales básicos y datos de contacto.</p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
@Entity
@Table(name = "persons")
public class Person {

    /**
     * Identificador interno de la persona (PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tipo de identificación de la persona (por ejemplo: CC, TI, CE, PAS, etc.).
     *
     * <p>Se persiste como texto (STRING) en la columna {@code id_type}.</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "id_type", nullable = false, length = 10)
    private IdType idType;

    /**
     * Número de identificación de la persona.
     *
     * <p>Se define como único para evitar duplicados en el registro.</p>
     */
    @Column(name = "id_number", nullable = false, length = 40, unique = true)
    private String idNumber;

    /**
     * Nombres de la persona.
     */
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    /**
     * Apellidos de la persona.
     */
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /**
     * Correo electrónico de la persona (opcional).
     */
    @Column(name = "email", length = 150)
    private String email;

    /**
     * Teléfono de la persona (opcional).
     */
    @Column(name = "phone", length = 50)
    private String phone;

    /**
     * Fecha de nacimiento de la persona (opcional).
     */
    @Column(name = "birthdate")
    private LocalDate birthdate;

    /**
     * Fecha/hora de creación del registro.
     *
     * <p>En esta implementación se inicializa con {@link LocalDateTime#now()}.
     * La columna está marcada como {@code updatable=false} para no modificarse en updates.</p>
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Documentos asociados a la persona.
     *
     * <p>Relación uno-a-muchos, mapeada por el atributo {@code person} en {@link PersonDocument}.</p>
     * <p>Se marca con {@link JsonIgnore} para evitar ciclos en serialización.</p>
     */
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
     * <p>Normalmente no se asigna manualmente porque es autogenerado.</p>
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
     * Retorna el correo electrónico.
     *
     * @return correo electrónico (puede ser {@code null})
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
     * @return teléfono (puede ser {@code null})
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
     * @return fecha de nacimiento (puede ser {@code null})
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
     * <p>Se hace no modificarla manualmente si se usa como auditoría.</p>
     *
     * @param createdAt fecha/hora de creación
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Retorna la lista de documentos asociados a la persona.
     *
     * @return lista de documentos
     */
    public List<PersonDocument> getPersonDocuments() {
        return personDocuments;
    }

    /**
     * Establece la lista de documentos asociados a la persona.
     *
     * @param personDocuments lista de documentos
     */
    public void setPersonDocuments(List<PersonDocument> personDocuments) {
        this.personDocuments = personDocuments;
    }

    /**
     * Retorna el nombre completo de la persona concatenando nombres y apellidos.
     *
     * <p>Este método no se persiste en base de datos ({@link Transient}).</p>
     *
     * @return nombre completo (nunca {@code null})
     */
    @Transient
    public String getFullName() {
        return (firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName);
    }
}
