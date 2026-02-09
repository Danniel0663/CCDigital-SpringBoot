package co.edu.unbosque.ccdigital.entity;

/**
 * Enum que representa el estado del flujo de revisión para un registro que requiere validación.
 *
 * <p>En CCDigital se utiliza, por ejemplo, para {@code PersonDocument} cuando un documento es cargado
 * y debe pasar por revisión administrativa antes de considerarse aprobado.</p>
 * 
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
public enum ReviewStatus {

    /**
     * Pendiente de revisión.
     */
    PENDING,

    /**
     * Revisado y aprobado.
     */
    APPROVED,

    /**
     * Revisado y rechazado.
     */
    REJECTED
}
