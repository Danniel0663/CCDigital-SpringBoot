package co.edu.unbosque.ccdigital.entity;

/**
 * Estado del flujo de revisión administrativa.
 *
 * <p>
 * Se utiliza para registros que requieren validación, por ejemplo en {@code PersonDocument}, cuando
 * un documento es cargado y debe ser aprobado o rechazado por un revisor.
 * </p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
public enum ReviewStatus {

    /** Pendiente de revisión. */
    PENDING,

    /** Revisado y aprobado. */
    APPROVED,

    /** Revisado y rechazado. */
    REJECTED
}
