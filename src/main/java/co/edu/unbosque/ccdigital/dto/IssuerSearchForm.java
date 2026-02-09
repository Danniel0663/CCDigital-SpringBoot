package co.edu.unbosque.ccdigital.dto;

import co.edu.unbosque.ccdigital.entity.IdType;

/**
 * DTO utilizado para capturar los datos del formulario de búsqueda en el módulo de Emisores (Issuer).
 *
 * <p>Normalmente se usa en el endpoint {@code POST /issuer/search} para redirigir a la vista principal
 * con el {@code issuerId} y el {@code personId} encontrados.</p>
 *
 * @author Danniel
 * @author Yeison
 * @since 1.0
 */
public class IssuerSearchForm {

    /**
     * Identificador del emisor seleccionado en la interfaz.
     */
    private Long issuerId;

    /**
     * Tipo de identificación de la persona (por ejemplo: CC, TI, CE, PAS, etc.).
     */
    private IdType idType;

    /**
     * Número de identificación de la persona.
     */
    private String idNumber;

    /**
     * Retorna el id del emisor seleccionado.
     *
     * @return id del emisor
     */
    public Long getIssuerId() {
        return issuerId;
    }

    /**
     * Establece el id del emisor seleccionado.
     *
     * @param issuerId id del emisor
     */
    public void setIssuerId(Long issuerId) {
        this.issuerId = issuerId;
    }

    /**
     * Retorna el tipo de identificación de la persona.
     *
     * @return tipo de identificación
     */
    public IdType getIdType() {
        return idType;
    }

    /**
     * Establece el tipo de identificación de la persona.
     *
     * @param idType tipo de identificación
     */
    public void setIdType(IdType idType) {
        this.idType = idType;
    }

    /**
     * Retorna el número de identificación de la persona.
     *
     * @return número de identificación
     */
    public String getIdNumber() {
        return idNumber;
    }

    /**
     * Establece el número de identificación de la persona.
     *
     * @param idNumber número de identificación
     */
    public void setIdNumber(String idNumber) {
        this.idNumber = idNumber;
    }
}
