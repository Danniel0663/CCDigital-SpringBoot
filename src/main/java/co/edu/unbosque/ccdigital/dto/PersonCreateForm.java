package co.edu.unbosque.ccdigital.dto;

import co.edu.unbosque.ccdigital.entity.IdType;

import java.time.LocalDate;

public class PersonCreateForm {
    private IdType idType;
    private String idNumber;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private LocalDate birthdate;

    public IdType getIdType() { return idType; }
    public void setIdType(IdType idType) { this.idType = idType; }

    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String idNumber) { this.idNumber = idNumber; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public LocalDate getBirthdate() { return birthdate; }
    public void setBirthdate(LocalDate birthdate) { this.birthdate = birthdate; }
}
