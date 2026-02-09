package co.edu.unbosque.ccdigital.dto;

import co.edu.unbosque.ccdigital.entity.IdType;

/**
 * DTO para búsqueda de personas por tipo y número de identificación.
 */
public class IssuerSearchForm {

    /**
     * Tipo de identificación.
     */
    private IdType idType;

    /**
     * Número de identificación.
     */
    private String idNumber;

    public IdType getIdType() { return idType; }
    public void setIdType(IdType idType) { this.idType = idType; }

    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String idNumber) { this.idNumber = idNumber; }
}
