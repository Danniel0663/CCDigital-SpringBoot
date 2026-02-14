package co.edu.unbosque.ccdigital.entity;

/**
 * Estado funcional de un documento asociado a una persona.
 *
 * <p>
 * Describe la vigencia o condición del documento en el tiempo, independiente del flujo de revisión
 * administrativa ({@link ReviewStatus}).
 * </p>
 *
 * @author Danniel
 * @author Yeison
 * @since 3.0
 */
public enum PersonDocumentStatus {

    /** El documento está vigente. */
    VIGENTE,

    /** El documento está vencido. */
    VENCIDO,

    /** El documento está en trámite. */
    EN_TRÁMITE,

    /** El documento fue anulado. */
    ANULADO
}
