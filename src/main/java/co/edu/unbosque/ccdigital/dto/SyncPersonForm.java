package co.edu.unbosque.ccdigital.dto;

import co.edu.unbosque.ccdigital.entity.IdType;

/**
 * DTO para capturar los datos del formulario de sincronización por persona en el módulo administrativo.
 *
 * <p>
 * Se utiliza para ejecutar procesos puntuales (por ejemplo, sincronización hacia Fabric) con base en
 * tipo y número de identificación.
 * </p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
public class SyncPersonForm {

    private IdType idType;
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
