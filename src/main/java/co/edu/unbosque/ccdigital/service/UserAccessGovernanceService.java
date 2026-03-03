package co.edu.unbosque.ccdigital.service;

import co.edu.unbosque.ccdigital.config.IndyProperties;
import co.edu.unbosque.ccdigital.entity.AppUser;
import co.edu.unbosque.ccdigital.entity.Person;
import co.edu.unbosque.ccdigital.entity.UserAccessState;
import co.edu.unbosque.ccdigital.repository.AppUserRepository;
import co.edu.unbosque.ccdigital.repository.PersonRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Servicio administrativo para gobernar el estado de acceso de usuarios finales.
 */
@Service
public class UserAccessGovernanceService {

    /**
     * Vista resumida del estado de acceso del usuario.
     */
    public record UserAccessView(
            Long personId,
            String email,
            String fullName,
            String role,
            Boolean isActive,
            String accessState,
            String reason,
            LocalDateTime updatedAt,
            Boolean indySynced,
            LocalDateTime indySyncAt,
            String indySyncError
    ) {}

    /**
     * Resultado del cambio de estado de acceso.
     */
    public record AccessUpdateResult(
            UserAccessView access,
            boolean indyCallAttempted,
            boolean indyCallSucceeded,
            String indyMessage
    ) {}

    private final AppUserRepository appUserRepository;
    private final PersonRepository personRepository;
    private final IndyAdminClient indyAdminClient;
    private final IndyProperties indyProperties;
    private final FabricAuditCliService fabricAuditCliService;

    public UserAccessGovernanceService(AppUserRepository appUserRepository,
                                       PersonRepository personRepository,
                                       IndyAdminClient indyAdminClient,
                                       IndyProperties indyProperties,
                                       FabricAuditCliService fabricAuditCliService) {
        this.appUserRepository = appUserRepository;
        this.personRepository = personRepository;
        this.indyAdminClient = indyAdminClient;
        this.indyProperties = indyProperties;
        this.fabricAuditCliService = fabricAuditCliService;
    }

    /**
     * Busca estado de acceso por personId si existe cuenta en users.
     */
    @Transactional(readOnly = true)
    public Optional<UserAccessView> findByPersonId(Long personId) {
        if (personId == null) {
            return Optional.empty();
        }
        return appUserRepository.findById(personId).map(this::toView);
    }

    /**
     * Actualiza estado de acceso de la cuenta y registra resultado de sincronización Indy.
     */
    @Transactional
    public AccessUpdateResult updateState(Long personId, UserAccessState targetState, String reason) {
        if (personId == null) {
            throw new IllegalArgumentException("personId es obligatorio");
        }
        if (targetState == null) {
            throw new IllegalArgumentException("El estado de acceso es obligatorio");
        }

        AppUser user = appUserRepository.findById(personId)
                .orElseThrow(() -> new IllegalArgumentException("No existe cuenta de usuario para la persona indicada"));

        if (!isEndUser(user)) {
            throw new IllegalArgumentException("Solo se puede gobernar acceso para cuentas de usuario final");
        }

        Long userPersonId = user.getPersonId();
        if (userPersonId == null) {
            throw new IllegalArgumentException("La cuenta de usuario no tiene person_id asociado");
        }

        Person person = personRepository.findById(userPersonId).orElse(null);
        user.setAccessState(targetState);
        user.setAccessStateReason(trimToNull(reason));
        user.setAccessStateUpdatedAt(LocalDateTime.now());
        user.setIsActive(targetState != UserAccessState.DISABLED);

        SyncResult syncResult = syncStateToIndy(user, person, targetState, reason);
        if (syncResult.attempted()) {
            user.setIndyAccessSynced(syncResult.success());
            user.setIndyAccessSyncAt(LocalDateTime.now());
            user.setIndyAccessSyncError(syncResult.errorMessage());
        } else {
            // Si no hubo intento real de sincronización, el estado visual debe verse como N/A.
            user.setIndyAccessSynced(null);
            user.setIndyAccessSyncAt(null);
            user.setIndyAccessSyncError(null);
        }

        AppUser saved = appUserRepository.save(user);
        AuditResult auditResult = recordStateChangeOnFabric(saved, person, targetState, reason, syncResult);
        String combinedMessage = buildResultMessage(syncResult, auditResult);
        return new AccessUpdateResult(
                toView(saved),
                syncResult.attempted(),
                syncResult.success(),
                combinedMessage
        );
    }

    private UserAccessView toView(AppUser user) {
        UserAccessState state = user.getAccessState() == null ? UserAccessState.ENABLED : user.getAccessState();
        return new UserAccessView(
                user.getPersonId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getIsActive(),
                state.name(),
                user.getAccessStateReason(),
                user.getAccessStateUpdatedAt(),
                user.getIndyAccessSynced(),
                user.getIndyAccessSyncAt(),
                user.getIndyAccessSyncError()
        );
    }

    private boolean isEndUser(AppUser user) {
        String role = normalize(user.getRole());
        if (role.startsWith("ROLE_")) {
            role = role.substring("ROLE_".length());
        }
        return "USER".equalsIgnoreCase(role) || "USUARIO".equalsIgnoreCase(role);
    }

    private SyncResult syncStateToIndy(AppUser user, Person person, UserAccessState targetState, String reason) {
        if (!Boolean.TRUE.equals(indyProperties.getUserAccessSyncEnabled())) {
            return new SyncResult(false, false, "Sincronización Indy deshabilitada por configuración", null);
        }

        String baseUrl = normalize(indyProperties.getIssuerAdminUrl());
        if (baseUrl.isBlank()) {
            return new SyncResult(false, false, "No hay issuer-admin-url configurada para sincronización Indy", "issuer-admin-url vacío");
        }
        String path = resolveSyncPath(baseUrl);
        if (path.isBlank()) {
            return new SyncResult(false, false, "No hay user-access-sync-path configurado", "user-access-sync-path vacío");
        }

        try {
            Map<String, Object> statePayload = buildStatePayload(user, person, targetState, reason);
            Map<String, Object> payload = buildPostPayload(path, statePayload);

            JsonNode response = indyAdminClient.post(baseUrl, path, payload);
            String msg = response != null
                    ? "Sincronización Indy completada en " + path
                    : "Sincronización Indy completada en " + path + " (sin payload)";
            return new SyncResult(true, true, msg, null);
        } catch (Exception ex) {
            String detail = ex.getMessage() == null ? "Error desconocido al sincronizar con Indy" : ex.getMessage();
            return new SyncResult(true, false, "Cambio local aplicado, pero falló sincronización Indy", detail);
        }
    }

    private Map<String, Object> buildStatePayload(AppUser user,
                                                  Person person,
                                                  UserAccessState targetState,
                                                  String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("personId", user.getPersonId());
        payload.put("email", user.getEmail());
        payload.put("state", targetState.name());
        payload.put("reason", trimToNull(reason));
        payload.put("updatedAt", LocalDateTime.now().toString());
        payload.put("idType", person != null && person.getIdType() != null ? person.getIdType().name() : null);
        payload.put("idNumber", person != null ? person.getIdNumber() : null);
        return payload;
    }

    private Map<String, Object> buildPostPayload(String path, Map<String, Object> statePayload) {
        if (isConnectionMetadataPath(path)) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put(resolveAccessMetadataKey(statePayload), statePayload);
            return Map.of("metadata", metadata);
        }
        return statePayload;
    }

    private String resolveSyncPath(String baseUrl) {
        String path = normalize(indyProperties.getUserAccessSyncPath());
        if (path.isBlank()) {
            return path;
        }
        if (path.contains("{conn_id}") || path.contains("{connection_id}")) {
            String connId = resolveHolderConnectionId(baseUrl);
            path = path.replace("{conn_id}", connId).replace("{connection_id}", connId);
        }
        return path;
    }

    private String resolveHolderConnectionId(String baseUrl) {
        String configured = normalize(indyProperties.getHolderConnectionId());
        if (!configured.isBlank() && !"auto".equalsIgnoreCase(configured)) {
            return configured;
        }

        String holderLabel = normalize(indyProperties.getHolderLabel());
        JsonNode response = indyAdminClient.get(baseUrl, "/connections?state=active");
        JsonNode results = response == null ? null : response.path("results");
        if (results == null || !results.isArray()) {
            throw new IllegalStateException("ACA-Py no devolvió el listado de conexiones activas");
        }

        for (JsonNode conn : results) {
            String label = normalize(conn.path("their_label").asText(null));
            String connId = normalize(conn.path("connection_id").asText(null));
            if (connId.isBlank()) {
                continue;
            }
            if (holderLabel.isBlank() || holderLabel.equalsIgnoreCase(label)) {
                return connId;
            }
        }

        if (holderLabel.isBlank()) {
            throw new IllegalStateException("No se encontró ninguna conexión activa para sincronizar estado de acceso");
        }
        throw new IllegalStateException(
                "No se encontró conexión ACTIVE con their_label='" + holderLabel + "' para sincronizar estado de acceso"
        );
    }

    private String resolveAccessMetadataKey(Map<String, Object> statePayload) {
        String idType = normalize(statePayload.get("idType") == null ? null : String.valueOf(statePayload.get("idType")));
        String idNumber = normalize(statePayload.get("idNumber") == null ? null : String.valueOf(statePayload.get("idNumber")));
        if (idType.isBlank()) {
            idType = "NA";
        }
        if (idNumber.isBlank()) {
            idNumber = "person-" + normalize(String.valueOf(statePayload.get("personId")));
        }
        return "ccdigital.user_access_state." + sanitizeForMetadataKey(idType) + "." + sanitizeForMetadataKey(idNumber);
    }

    private String sanitizeForMetadataKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return "na";
        }
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }

    private boolean isConnectionMetadataPath(String path) {
        String normalized = normalize(path);
        return normalized.matches("^/connections/[^/]+/metadata(?:\\?.*)?$");
    }

    private AuditResult recordStateChangeOnFabric(AppUser user,
                                                  Person person,
                                                  UserAccessState targetState,
                                                  String reason,
                                                  SyncResult syncResult) {
        String idType = person != null && person.getIdType() != null ? person.getIdType().name() : "";
        String idNumber = person == null ? "" : normalize(person.getIdNumber());
        if (idType.isBlank() || idNumber.isBlank()) {
            return new AuditResult(false, "No se registró auditoría Fabric: faltan idType/idNumber de la persona.");
        }

        String syncTag = syncResult.attempted()
                ? (syncResult.success() ? "SYNC_INDY_OK" : "SYNC_INDY_FAIL")
                : "SYNC_INDY_SKIPPED";
        String auditResult = (syncResult.attempted() && !syncResult.success()) ? "FAIL" : "OK";
        String syncDetail = syncResult.attempted()
                ? (syncResult.success() ? "Sincronización Indy exitosa." : "Sincronización Indy fallida.")
                : "Sincronización Indy no ejecutada.";
        String fullReason = mergeReason(reason, syncTag + " - " + syncDetail);

        try {
            fabricAuditCliService.recordEvent(new FabricAuditCliService.AuditCommand(
                    idType,
                    idNumber,
                    "USER_ACCESS_STATE_CHANGE",
                    null,
                    null,
                    null,
                    "Estado de acceso de usuario",
                    null,
                    "CCDigital Admin",
                    targetState.name(),
                    auditResult,
                    fullReason,
                    "ADMIN",
                    String.valueOf(user.getPersonId()),
                    "ADMIN_GOVERNANCE"
            ));
            return new AuditResult(true, "Evento registrado en Fabric.");
        } catch (Exception ex) {
            String detail = normalize(ex.getMessage());
            if (detail.isBlank()) {
                detail = "No se pudo registrar el evento en Fabric.";
            }
            return new AuditResult(false, detail);
        }
    }

    private String buildResultMessage(SyncResult syncResult, AuditResult auditResult) {
        String syncMessage = normalize(syncResult.message());
        String auditMessage = normalize(auditResult.message());
        if (syncMessage.isBlank()) {
            return auditMessage.isBlank() ? "Proceso completado." : auditMessage;
        }
        if (auditMessage.isBlank()) {
            return syncMessage;
        }
        return syncMessage + " " + auditMessage;
    }

    private String mergeReason(String reason, String suffix) {
        String base = normalize(reason);
        String extra = normalize(suffix);
        if (base.isBlank()) {
            return extra;
        }
        if (extra.isBlank()) {
            return base;
        }
        return base + " | " + extra;
    }

    private static String trimToNull(String value) {
        String out = normalize(value);
        return out.isBlank() ? null : out;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private record SyncResult(
            boolean attempted,
            boolean success,
            String message,
            String errorMessage
    ) {}

    private record AuditResult(
            boolean success,
            String message
    ) {}
}
