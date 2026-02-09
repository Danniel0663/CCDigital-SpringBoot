package co.edu.unbosque.ccdigital.entity;

/**
 * Enum que representa los tipos de identificación soportados por el sistema.
 *
 * <p>Se utiliza para clasificar el documento de identidad de una persona (por ejemplo en {@code Person})
 * y para búsquedas/formularios donde se requiere el tipo junto con el número.</p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
public enum IdType {

    /**
     * Cédula de Ciudadanía.
     */
    CC,

    /**
     * Cédula de Extranjería.
     */
    CE,

    /**
     * Pasaporte.
     */
    PA,

    /**
     * Número de Identificación Tributaria (empresas/personas jurídicas).
     */
    NIT,

    /**
     * Tarjeta de Identidad.
     */
    TI,

    /**
     * Permiso Especial de Permanencia.
     */
    PEP,

    /**
     * Otro tipo de identificación.
     */
    OTRO
}
