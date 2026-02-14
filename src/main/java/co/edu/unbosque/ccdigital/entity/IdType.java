package co.edu.unbosque.ccdigital.entity;

/**
 * Tipos de identificación soportados por el sistema.
 *
 * <p>
 * Se utiliza para clasificar el documento de identidad de una persona y para operaciones que requieren
 * el tipo y el número de identificación.
 * </p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
public enum IdType {

    /** Cédula de ciudadanía. */
    CC,

    /** Cédula de extranjería. */
    CE,

    /** Pasaporte. */
    PA,

    /** Número de identificación tributaria. */
    NIT,

    /** Tarjeta de identidad. */
    TI,

    /** Permiso especial de permanencia. */
    PEP,

    /** Otro tipo de identificación. */
    OTRO
}
