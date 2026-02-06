package co.edu.unbosque.ccdigital.dto;

import co.edu.unbosque.ccdigital.entity.IdType;

public class SyncPersonForm {
    private IdType idType;
    private String idNumber;

    public IdType getIdType() { return idType; }
    public void setIdType(IdType idType) { this.idType = idType; }

    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String idNumber) { this.idNumber = idNumber; }
}
