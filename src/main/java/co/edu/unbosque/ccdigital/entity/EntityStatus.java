package co.edu.unbosque.ccdigital.entity;

/**
 * Enum que representa el estado de una entidad dentro del flujo de aprobación del sistema.
 *
 * <p>Se utiliza para modelar el ciclo de vida de aprobación típico, por ejemplo para
 * entidades emisoras y registros pendientes de validación</p>
 *
 * @author Danniel
 * @author Yeison
 * @since 1.0
 */
public enum EntityStatus {

    /**
     * La entidad fue aprobada y está habilitada para su uso.
     */
    APROBADA,

    /**
     * La entidad se encuentra pendiente de revisión o aprobación.
     */
    PENDIENTE,

    /**
     * La entidad fue rechazada y no está habilitada para su uso.
     */
    RECHAZADA
}
