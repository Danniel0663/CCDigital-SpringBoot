package co.edu.unbosque.ccdigital.entity;

/**
 * Estados posibles de una solicitud de acceso.
 *
 * Recomendación de uso:
 * - PENDIENTE: estado inicial cuando el emisor crea la solicitud.
 * - APROBADA: el usuario autoriza la consulta de los documentos solicitados.
 * - RECHAZADA: el usuario niega la solicitud.
 * - EXPIRADA: la solicitud ya no es válida (por tiempo o regla de negocio).
 *
 * Nota:
 * Este enum se persiste como texto si se usa @Enumerated(EnumType.STRING) en la entidad AccessRequest.
 */
public enum AccessRequestStatus {
    PENDIENTE,
    APROBADA,
    RECHAZADA,
    EXPIRADA
}
