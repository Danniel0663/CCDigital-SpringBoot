package co.edu.unbosque.ccdigital.dto;

import co.edu.unbosque.ccdigital.entity.IdType;

/**
 * DTO para capturar los criterios de búsqueda de una persona desde la vista del emisor (Issuer).
 *
 * @since 3.0
 */
public class IssuerSearchForm {

    /**
     * Tipo de identificación de la persona.
     */
    private IdType idType;

    /**
     * Número de identificación de la persona.
     */
    private String idNumber;

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
}
