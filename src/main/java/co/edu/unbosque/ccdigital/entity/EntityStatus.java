package co.edu.unbosque.ccdigital.entity;

/**
 * Estado del flujo de aprobación de una entidad dentro del sistema.
 *
 * <p>
 * Se utiliza para modelar el ciclo de vida de aprobación, por ejemplo, en entidades emisoras
 * y registros que requieren validación administrativa.
 * </p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
public enum EntityStatus {

    /** La entidad fue aprobada y está habilitada para su uso. */
    APROBADA,

    /** La entidad se encuentra pendiente de revisión o aprobación. */
    PENDIENTE,

    /** La entidad fue rechazada y no está habilitada para su uso. */
    RECHAZADA
}
