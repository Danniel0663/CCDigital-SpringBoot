package co.edu.unbosque.ccdigital.controller;

import co.edu.unbosque.ccdigital.entity.AccessRequest;
import co.edu.unbosque.ccdigital.entity.AccessRequestStatus;
import co.edu.unbosque.ccdigital.entity.IdType;
import co.edu.unbosque.ccdigital.entity.IssuingEntity;
import co.edu.unbosque.ccdigital.entity.Person;
import co.edu.unbosque.ccdigital.repository.IssuingEntityRepository;
import co.edu.unbosque.ccdigital.repository.PersonRepository;
import co.edu.unbosque.ccdigital.security.IndyUserPrincipal;
import co.edu.unbosque.ccdigital.security.IssuerPrincipal;
import co.edu.unbosque.ccdigital.service.AccessRequestService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * Inyecta en las vistas un contexto de perfil activo para módulos web (usuario, emisor y admin).
 *
 * <p>Objetivo:</p>
 * <ul>
 *   <li>Mostrar en la UI qué perfil está usando la sesión actual.</li>
 *   <li>Exponer datos de persona en el módulo de usuario sin repetir consultas en cada controlador.</li>
 *   <li>Exponer datos de entidad en el módulo emisor para una visualización consistente.</li>
 * </ul>
 *
 * <p>La información se agrega como atributos de modelo con prefijo {@code cc} para evitar colisiones
 * con atributos específicos de cada vista.</p>
 */
@ControllerAdvice(annotations = Controller.class)
public class UiProfileModelAdvice {

    private static final DateTimeFormatter UI_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter UI_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MAX_NOTIFICATIONS = 6;
    private static final int ISSUER_RECENT_DECISIONS_DAYS = 7;
    private static final String SESSION_ISSUER_NOTIFS_LAST_SEEN = "cc_issuer_notifs_last_seen";

    private final PersonRepository personRepository;
    private final IssuingEntityRepository issuingEntityRepository;
    private final AccessRequestService accessRequestService;

    /**
     * Constructor con repositorios necesarios para enriquecer el perfil de la sesión.
     *
     * @param personRepository repositorio de personas (perfil de usuario final)
     * @param issuingEntityRepository repositorio de entidades emisoras (perfil de emisor)
     * @param accessRequestService servicio para listar solicitudes y construir notificaciones UI
     */
    public UiProfileModelAdvice(PersonRepository personRepository,
                                IssuingEntityRepository issuingEntityRepository,
                                AccessRequestService accessRequestService) {
        this.personRepository = personRepository;
        this.issuingEntityRepository = issuingEntityRepository;
        this.accessRequestService = accessRequestService;
    }

    /**
     * Agrega al modelo el perfil activo según el módulo de la URL.
     *
     * <p>Se omiten endpoints AJAX/técnicos del módulo usuario ({@code /user/auth/**}) para evitar
     * consultas innecesarias cuando la respuesta no renderiza una vista. Además, expone la ruta
     * actual ({@code ccCurrentUri}) para que los templates resalten acciones activas del navbar
     * (por ejemplo, Solicitudes).</p>
     *
     * @param authentication autenticación actual
     * @param request request HTTP actual
     * @param model modelo de la vista
     */
    @ModelAttribute
    public void addActiveProfile(Authentication authentication, HttpServletRequest request, Model model) {
        if (authentication == null || !authentication.isAuthenticated() || request == null) {
            return;
        }

        String uri = request.getRequestURI();
        if (uri == null || uri.isBlank() || uri.startsWith("/user/auth/")) {
            return;
        }
        // Se usa en Thymeleaf para resaltar botones de navegación activos por ruta.
        model.addAttribute("ccCurrentUri", uri);

        Object principal = authentication.getPrincipal();

        if (uri.startsWith("/user/") && principal instanceof IndyUserPrincipal p) {
            UserProfileView userProfile = buildUserProfile(p);
            model.addAttribute("ccUserProfile", userProfile);
            model.addAttribute("ccActiveProfileKind", "USER");
            model.addAttribute("ccActiveProfileLabel", "Perfil usuario");
            model.addAttribute("ccActiveProfileIcon", "bi-person-badge");
            model.addAttribute("ccActiveProfileDisplay", userProfile.displayName());
            addUserNotifications(model, userProfile);
            return;
        }

        if ((uri.equals("/issuer") || uri.startsWith("/issuer/")) && principal instanceof IssuerPrincipal p) {
            // Si el usuario llega desde una notificación (click en tarjeta), se considera leída
            // y se limpia el resaltado/contador nuevo del módulo emisor.
            IssuerProfileView issuerProfile = buildIssuerProfile(p);
            if ("true".equalsIgnoreCase(request.getParameter("notifRead"))) {
                markIssuerNotificationsSeen(request, issuerProfile.issuerId());
            }
            model.addAttribute("ccIssuerProfile", issuerProfile);
            model.addAttribute("ccActiveProfileKind", "ISSUER");
            model.addAttribute("ccActiveProfileLabel", "Usuario emisor");
            model.addAttribute("ccActiveProfileIcon", "bi-building");
            model.addAttribute("ccActiveProfileDisplay", issuerProfile.username());
            addIssuerNotifications(model, issuerProfile);
            return;
        }

        if (uri.startsWith("/admin/")) {
            model.addAttribute("ccActiveProfileKind", "ADMIN");
            model.addAttribute("ccActiveProfileLabel", "Perfil admin");
            model.addAttribute("ccActiveProfileIcon", "bi-shield-lock");
            model.addAttribute("ccActiveProfileDisplay", safe(authentication.getName()));
        }
    }

    /**
     * Construye una vista de perfil de usuario con datos del principal y de la tabla {@code persons}.
     *
     * <p>Se usa el principal como fuente base y la tabla {@code persons} como enriquecimiento
     * (correo/teléfono/fecha, si están disponibles).</p>
     *
     * @param principal principal autenticado del usuario final
     * @return vista de perfil lista para UI
     */
    private UserProfileView buildUserProfile(IndyUserPrincipal principal) {
        String idType = safe(principal.getIdType());
        String idNumber = safe(principal.getIdNumber());
        String displayName = safe(principal.getDisplayName());
        String email = safe(principal.getEmail());
        String phone = "";
        String birthdateHuman = "";
        Long personId = null;

        try {
            if (!idType.isBlank() && !idNumber.isBlank()) {
                IdType enumIdType = IdType.valueOf(idType);
                Person person = personRepository.findByIdTypeAndIdNumber(enumIdType, idNumber).orElse(null);
                if (person != null) {
                    personId = person.getId();
                    if (person.getFullName() != null && !person.getFullName().isBlank()) {
                        displayName = person.getFullName();
                    }
                    if (person.getEmail() != null && !person.getEmail().isBlank()) {
                        email = person.getEmail().trim();
                    }
                    phone = safe(person.getPhone());
                    birthdateHuman = person.getBirthdate() != null ? UI_DATE.format(person.getBirthdate()) : "";
                }
            }
        } catch (Exception ignored) {
            // Si falla el enriquecimiento (enum inválido o dato inconsistente), se conserva el perfil base del principal.
        }

        return new UserProfileView(personId, displayName, idType, idNumber, email, phone, birthdateHuman);
    }

    /**
     * Construye una vista de perfil de emisor a partir del principal y la entidad asociada.
     *
     * @param principal principal del emisor autenticado
     * @return vista de perfil del emisor para UI
     */
    private IssuerProfileView buildIssuerProfile(IssuerPrincipal principal) {
        Long issuerId = principal.getIssuerId();
        String entityName = "Entidad emisora";
        String entityType = "";
        String entityStatus = "";

        if (issuerId != null) {
            IssuingEntity entity = issuingEntityRepository.findById(issuerId).orElse(null);
            if (entity != null) {
                entityName = safe(entity.getName(), entityName);
                entityType = entity.getEntityType() != null ? entity.getEntityType().name() : "";
                entityStatus = entity.getStatus() != null ? entity.getStatus().name() : "";
            }
        }

        return new IssuerProfileView(
                issuerId,
                entityName,
                safe(principal.getUsername()),
                entityType,
                entityStatus
        );
    }

    /**
     * Agrega notificaciones para el módulo usuario (solicitudes nuevas/pedientes de decisión).
     *
     * @param model modelo Thymeleaf
     * @param userProfile perfil de usuario resuelto
     */
    private void addUserNotifications(Model model, UserProfileView userProfile) {
        if (userProfile == null || userProfile.personId() == null) {
            model.addAttribute("ccNotifications", List.of());
            model.addAttribute("ccNotificationCount", 0);
            model.addAttribute("ccNotificationHighlight", false);
            return;
        }

        List<UiNotificationView> notifications = accessRequestService.listForPerson(userProfile.personId()).stream()
                .filter(r -> r != null && r.getStatus() == AccessRequestStatus.PENDIENTE)
                .sorted(Comparator.comparing(AccessRequest::getRequestedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_NOTIFICATIONS)
                .map(r -> new UiNotificationView(
                        "bi-inbox-fill",
                        "Nueva solicitud de acceso",
                        buildUserNotificationMessage(r),
                        formatDateTime(r.getRequestedAt()),
                        "/user/requests",
                        "warning"
                ))
                .toList();

        model.addAttribute("ccNotifications", notifications);
        model.addAttribute("ccNotificationCount", notifications.size());
        model.addAttribute("ccNotificationHighlight", !notifications.isEmpty());
        model.addAttribute("ccNotificationTitle", "Notificaciones del usuario");
        model.addAttribute("ccNotificationEmptyMessage", "No tienes solicitudes nuevas pendientes de decisión.");
    }

    /**
     * Agrega notificaciones para el módulo emisor (respuestas del usuario sobre solicitudes enviadas).
     *
     * <p>Se muestran respuestas recientes (últimos días) para evitar mantener el botón resaltado
     * permanentemente por historial antiguo. También se filtran por la marca "última revisión"
     * guardada en sesión para que el emisor vea solo respuestas nuevas desde su último acceso
     * al panel/listado.</p>
     *
     * @param model modelo Thymeleaf
     * @param issuerProfile perfil de emisor resuelto
     */
    private void addIssuerNotifications(Model model, IssuerProfileView issuerProfile) {
        if (issuerProfile == null || issuerProfile.issuerId() == null) {
            model.addAttribute("ccNotifications", List.of());
            model.addAttribute("ccNotificationCount", 0);
            model.addAttribute("ccNotificationHighlight", false);
            return;
        }

        LocalDateTime since = LocalDateTime.now().minusDays(ISSUER_RECENT_DECISIONS_DAYS);
        LocalDateTime lastSeen = getIssuerNotificationsLastSeen();

        List<UiNotificationView> notifications = accessRequestService.listForEntity(issuerProfile.issuerId()).stream()
                .filter(r -> r != null && r.getStatus() != null && r.getStatus() != AccessRequestStatus.PENDIENTE)
                .filter(r -> r.getDecidedAt() != null && !r.getDecidedAt().isBefore(since))
                .filter(r -> lastSeen == null || r.getDecidedAt().isAfter(lastSeen))
                .sorted(Comparator.comparing(AccessRequest::getDecidedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_NOTIFICATIONS)
                .map(r -> new UiNotificationView(
                        "bi-reply-fill",
                        "Respuesta a solicitud #" + r.getId(),
                        buildIssuerNotificationMessage(r),
                        formatDateTime(r.getDecidedAt()),
                        "/issuer/access-requests?notifRead=true",
                        toneForStatus(r.getStatus())
                ))
                .toList();

        model.addAttribute("ccNotifications", notifications);
        model.addAttribute("ccNotificationCount", notifications.size());
        model.addAttribute("ccNotificationHighlight", !notifications.isEmpty());
        model.addAttribute("ccNotificationTitle", "Notificaciones del emisor");
        model.addAttribute("ccNotificationEmptyMessage", "No hay respuestas nuevas de solicitudes en los últimos días.");
    }

    /**
     * Construye el texto descriptivo de una notificación del módulo usuario.
     *
     * @param r solicitud de acceso
     * @return mensaje breve para tarjeta de notificación
     */
    private String buildUserNotificationMessage(AccessRequest r) {
        String entityName = (r.getEntity() != null && r.getEntity().getName() != null && !r.getEntity().getName().isBlank())
                ? r.getEntity().getName().trim()
                : "Entidad emisora";
        String purpose = r.getPurpose() != null && !r.getPurpose().isBlank()
                ? r.getPurpose().trim()
                : "Sin motivo especificado";
        return entityName + " envió una solicitud. Motivo: " + purpose;
    }

    /**
     * Construye el texto descriptivo de una notificación del módulo emisor.
     *
     * @param r solicitud de acceso respondida
     * @return mensaje breve para tarjeta de notificación
     */
    private String buildIssuerNotificationMessage(AccessRequest r) {
        String personName = (r.getPerson() != null && r.getPerson().getFullName() != null && !r.getPerson().getFullName().isBlank())
                ? r.getPerson().getFullName()
                : "Usuario";
        String status = r.getStatus() != null ? r.getStatus().name() : "RESPONDIDA";
        return personName + " respondió la solicitud con estado " + status + ".";
    }

    /**
     * Asigna un tono visual (Bootstrap/custom) según el estado de la solicitud.
     *
     * @param status estado de la solicitud
     * @return tono visual para icono/tarjeta de notificación
     */
    private String toneForStatus(AccessRequestStatus status) {
        if (status == null) return "info";
        return switch (status) {
            case APROBADA -> "success";
            case RECHAZADA -> "danger";
            case EXPIRADA -> "secondary";
            default -> "warning";
        };
    }

    /**
     * Formatea fecha/hora en formato de UI para tarjetas de notificación.
     *
     * @param value fecha/hora a formatear
     * @return texto formateado o vacío si es nulo
     */
    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : UI_DATE_TIME.format(value);
    }

    /**
     * Marca las notificaciones del emisor como vistas en la sesión actual.
     *
     * @param request request HTTP actual
     * @param issuerId id de la entidad emisora actual (para calcular la última respuesta real)
     */
    private void markIssuerNotificationsSeen(HttpServletRequest request, Long issuerId) {
        if (request == null) return;
        HttpSession session = request.getSession(false);
        if (session == null) return;

        // Se usa "now" como base, pero si la última decisión persistida es posterior
        // (por zona horaria o precisión de timestamps), se toma esa marca para limpiar bien.
        LocalDateTime marker = LocalDateTime.now();
        if (issuerId != null) {
            LocalDateTime latestDecision = accessRequestService.listForEntity(issuerId).stream()
                    .map(AccessRequest::getDecidedAt)
                    .filter(v -> v != null)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
            if (latestDecision != null && latestDecision.isAfter(marker)) {
                marker = latestDecision;
            }
        }

        // Se suma 1 ns para que el filtro "isAfter(lastSeen)" no vuelva a incluir
        // decisiones con exactamente la misma marca temporal.
        session.setAttribute(SESSION_ISSUER_NOTIFS_LAST_SEEN, marker.plusNanos(1));
    }

    /**
     * Obtiene la fecha/hora de última revisión de notificaciones del emisor desde la sesión.
     *
     * <p>Si no existe o el valor no tiene el tipo esperado, retorna {@code null}.</p>
     *
     * @return última fecha de lectura o {@code null}
     */
    private LocalDateTime getIssuerNotificationsLastSeen() {
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof org.springframework.web.context.request.ServletRequestAttributes sra)) {
            return null;
        }
        HttpSession session = sra.getRequest().getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(SESSION_ISSUER_NOTIFS_LAST_SEEN);
        return (value instanceof LocalDateTime ldt) ? ldt : null;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safe(String value, String fallback) {
        String v = safe(value);
        return v.isBlank() ? fallback : v;
    }

    /**
     * Vista de perfil de usuario final (módulo usuario).
     *
     * @param personId id interno de la persona (si se pudo resolver)
     * @param displayName nombre a mostrar
     * @param idType tipo de identificación
     * @param idNumber número de identificación
     * @param email correo registrado
     * @param phone teléfono registrado
     * @param birthdateHuman fecha de nacimiento formateada para UI
     */
    public record UserProfileView(
            Long personId,
            String displayName,
            String idType,
            String idNumber,
            String email,
            String phone,
            String birthdateHuman
    ) {}

    /**
     * Vista de perfil de entidad emisora (módulo emisor).
     *
     * @param issuerId id interno de la entidad emisora
     * @param entityName nombre de la entidad
     * @param username usuario autenticado (normalmente correo)
     * @param entityType tipo de entidad
     * @param entityStatus estado de la entidad
     */
    public record IssuerProfileView(
            Long issuerId,
            String entityName,
            String username,
            String entityType,
            String entityStatus
    ) {}

    /**
     * Vista de notificación para barra superior de módulos usuario/emisor.
     *
     * @param iconClass ícono Bootstrap (sin clase base {@code bi})
     * @param title título corto de la notificación
     * @param message descripción resumida
     * @param timeHuman fecha/hora legible
     * @param targetUrl ruta sugerida para consultar el detalle
     * @param tone tono visual (warning/success/danger/info/secondary)
     */
    public record UiNotificationView(
            String iconClass,
            String title,
            String message,
            String timeHuman,
            String targetUrl,
            String tone
    ) {}
}
