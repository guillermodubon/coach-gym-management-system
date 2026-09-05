package io.github.guillermodubon.coachgym.notification.web;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.notification.NotificationResourceType;
import io.github.guillermodubon.coachgym.notification.NotificationSeverity;
import io.github.guillermodubon.coachgym.notification.NotificationType;
import io.github.guillermodubon.coachgym.notification.application.NotificationApplicationService;
import io.github.guillermodubon.coachgym.notification.application.NotificationNotFoundException;
import io.github.guillermodubon.coachgym.notification.application.NotificationReadFilter;
import io.github.guillermodubon.coachgym.notification.application.NotificationSearchQuery;
import io.github.guillermodubon.coachgym.notification.application.NotificationSortDirection;
import io.github.guillermodubon.coachgym.notification.application.NotificationSortField;
import io.github.guillermodubon.coachgym.notification.domain.NotificationValidationException;
import io.github.guillermodubon.coachgym.shared.web.ApiProblemFactory;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(
        name = "Internal Notifications",
        description = "Recipient-scoped internal notification inbox.")
@SecurityRequirement(name = "sessionCookie")
public class NotificationController {

    private final NotificationApplicationService service;

    public NotificationController(NotificationApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "List my notifications",
            description = "ADMIN and RECEPTIONIST can list only their own notifications.")
    NotificationPageResponse findAll(
            @RequestParam(defaultValue = "ALL") String read,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdUntil,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "CREATED_AT") String sort,
            @RequestParam(defaultValue = "DESC") String direction,
            Authentication authentication) {

        NotificationSearchQuery query = new NotificationSearchQuery(
                NotificationReadFilter.from(read),
                parseEnum(type, NotificationType.class, "notification type"),
                parseEnum(severity, NotificationSeverity.class, "notification severity"),
                parseEnum(resourceType, NotificationResourceType.class, "resource type"),
                createdFrom,
                createdUntil,
                page,
                size,
                NotificationSortField.from(sort),
                NotificationSortDirection.from(direction));

        return NotificationPageResponse.from(
                service.findAll(query, actor(authentication)));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get my notification",
            description = "Returns 404 when the notification does not belong to the authenticated user.")
    NotificationResponse findById(
            @PathVariable UUID id,
            Authentication authentication) {
        return NotificationResponse.from(
                service.findById(id, actor(authentication)));
    }

    @GetMapping("/unread-count")
    @Operation(
            summary = "Count my unread notifications",
            description = "ADMIN and RECEPTIONIST can count only their own unread notifications.")
    NotificationUnreadCountResponse unreadCount(Authentication authentication) {
        return NotificationUnreadCountResponse.from(
                service.countUnread(actor(authentication)));
    }

    @PostMapping("/{id}/read")
    @Operation(
            summary = "Mark my notification as read",
            description = "Idempotent recipient-scoped operation. Requires CSRF.")
    NotificationResponse markAsRead(
            @PathVariable UUID id,
            Authentication authentication) {
        return NotificationResponse.from(
                service.markAsRead(id, actor(authentication)));
    }

    @PostMapping("/read-all")
    @Operation(
            summary = "Mark all my notifications as read",
            description = "Marks only the authenticated user's unread notifications. Requires CSRF.")
    NotificationUnreadCountResponse markAllAsRead(Authentication authentication) {
        return NotificationUnreadCountResponse.from(
                service.markAllAsRead(actor(authentication)));
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(NotificationNotFoundException exception) {
        return problem(
                HttpStatus.NOT_FOUND,
                "NOTIFICATION_NOT_FOUND",
                exception.getMessage());
    }

    @ExceptionHandler(NotificationValidationException.class)
    ResponseEntity<ProblemDetail> validation(NotificationValidationException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "NOTIFICATION_VALIDATION_FAILED",
                exception.getMessage());
    }

    private static <E extends Enum<E>> E parseEnum(
            String value,
            Class<E> type,
            String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new NotificationValidationException(
                    "Unsupported " + label + ": " + value + ".");
        }
    }

    private static AuthenticatedActor actor(Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof CoachGymUserPrincipal principal) {
            return principal.authenticatedActor();
        }
        throw new IllegalStateException(
                "Authentication principal is missing or invalid.");
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String code,
            String detail) {
        return ResponseEntity.status(status)
                .body(ApiProblemFactory.create(status, code, detail));
    }
}
