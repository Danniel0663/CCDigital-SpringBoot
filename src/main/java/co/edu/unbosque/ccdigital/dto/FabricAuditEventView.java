package co.edu.unbosque.ccdigital.dto;

/**
 * Vista tipada de un evento de auditoría almacenado en Hyperledger Fabric.
 *
 * <p>Representa el payload retornado por el chaincode {@code cddoc} para eventos
 * registrados mediante {@code recordAccessEvent}.</p>
 *
 * @param txId identificador de transacción en Fabric
 * @param idType tipo de identificación de la persona
 * @param idNumber número de identificación de la persona
 * @param eventType tipo funcional del evento (REQUEST_CREATED, DOC_VERIFY_ON_REQUEST, etc.)
 * @param requestId id de la solicitud relacionada (si aplica)
 * @param personDocumentId id del documento local (si aplica)
 * @param docId id de documento on-chain (si aplica)
 * @param documentTitle título del documento (si aplica)
 * @param issuerEntityId id de entidad emisora (si aplica)
 * @param issuerName nombre de entidad emisora (si aplica)
 * @param action acción técnica registrada
 * @param result resultado del evento (OK/FAIL/...)
 * @param reason detalle del resultado
 * @param actorType tipo de actor que originó el evento
 * @param actorId identificador del actor
 * @param source fuente lógica del evento
 * @param createdAt fecha/hora ISO del evento on-chain
 */
public record FabricAuditEventView(
        String txId,
        String idType,
        String idNumber,
        String eventType,
        String requestId,
        String personDocumentId,
        String docId,
        String documentTitle,
        String issuerEntityId,
        String issuerName,
        String action,
        String result,
        String reason,
        String actorType,
        String actorId,
        String source,
        String createdAt
) {
}
