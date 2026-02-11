package co.edu.unbosque.ccdigital.dto;

import co.edu.unbosque.ccdigital.entity.IdType;

/**
 * DTO utilizado para capturar los datos del formulario de sincronización
 * de una persona en la interfaz web del módulo administrativo.
 *
 * <p>Este formulario se usa típicamente para ejecutar una sincronización hacia herramientas externas
 * como Hyperledger Fabric de manera puntual para una persona específica</p>
 *
 * <p>Mapea con {@code @ModelAttribute("personForm")} en endpoints como
 * {@code POST /admin/sync/fabric/person}.</p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
public class SyncPersonForm {

    /**
     * Tipo de identificación de la persona (por ejemplo: CC, TI, CE, PAS, etc.).
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
