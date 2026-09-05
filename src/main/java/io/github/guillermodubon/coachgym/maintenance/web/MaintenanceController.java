package io.github.guillermodubon.coachgym.maintenance.web;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.equipment.EquipmentMaintenanceNotFoundException;
import io.github.guillermodubon.coachgym.equipment.EquipmentMaintenanceStateConflictException;
import io.github.guillermodubon.coachgym.equipment.EquipmentMaintenanceVersionConflictException;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceStatus;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceType;
import io.github.guillermodubon.coachgym.maintenance.application.*;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceValidationException;
import io.github.guillermodubon.coachgym.shared.web.ApiProblemFactory;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/maintenances")
@Tag(name = "Maintenance Work Orders", description = "Preventive and corrective equipment maintenance management.")
@SecurityRequirement(name = "sessionCookie")
public class MaintenanceController {

    private final MaintenanceApplicationService service;

    public MaintenanceController(MaintenanceApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Schedule maintenance", description = "ADMIN only. Requires CSRF.")
    ResponseEntity<MaintenanceResponse> schedule(
            @Valid @RequestBody ScheduleMaintenanceRequest request,
            Authentication authentication) {
        MaintenanceResponse response = MaintenanceResponse.from(
                service.schedule(request.toCommand(), actor(authentication)));
        return ResponseEntity.created(
                URI.create("/api/v1/maintenances/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update scheduled maintenance", description = "ADMIN only. Requires CSRF and optimistic locking.")
    MaintenanceResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateScheduledMaintenanceRequest request,
            Authentication authentication) {
        return MaintenanceResponse.from(
                service.updateScheduled(request.toCommand(id), actor(authentication)));
    }

    @GetMapping("/{id}")
    MaintenanceResponse findById(@PathVariable UUID id) {
        return MaintenanceResponse.from(service.findById(id));
    }

    @GetMapping
    MaintenancePageResponse findAll(
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) UUID incidentId,
            @RequestParam(required = false) String maintenanceType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate scheduledFrom,
            @RequestParam(required = false) LocalDate scheduledUntil,
            @RequestParam(required = false) UUID createdByUserId,
            @RequestParam(required = false) UUID assignedToUserId,
            @RequestParam(required = false) String providerName,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "SCHEDULED_ON") String sort,
            @RequestParam(defaultValue = "ASC") String direction) {
        MaintenanceSearchQuery query = new MaintenanceSearchQuery(
                equipmentId, incidentId,
                parseEnum(maintenanceType, MaintenanceType.class, "maintenance type"),
                parseEnum(status, MaintenanceStatus.class, "maintenance status"),
                scheduledFrom, scheduledUntil, createdByUserId, assignedToUserId,
                providerName, search, page, size,
                MaintenanceSortField.from(sort), MaintenanceSortDirection.from(direction));
        return MaintenancePageResponse.from(service.findAll(query));
    }

    @GetMapping("/{id}/history")
    List<MaintenanceStatusHistoryResponse> history(@PathVariable UUID id) {
        return service.findStatusHistory(id).stream()
                .map(MaintenanceStatusHistoryResponse::from).toList();
    }

    @PostMapping("/{id}/start")
    MaintenanceResponse start(
            @PathVariable UUID id,
            @Valid @RequestBody StartMaintenanceRequest request,
            Authentication authentication) {
        return MaintenanceResponse.from(
                service.start(request.toCommand(id), actor(authentication)));
    }

    @PostMapping("/{id}/complete")
    MaintenanceResponse complete(
            @PathVariable UUID id,
            @Valid @RequestBody CompleteMaintenanceRequest request,
            Authentication authentication) {
        return MaintenanceResponse.from(
                service.complete(request.toCommand(id), actor(authentication)));
    }

    @PostMapping("/{id}/cancel")
    MaintenanceResponse cancel(
            @PathVariable UUID id,
            @Valid @RequestBody CancelMaintenanceRequest request,
            Authentication authentication) {
        return MaintenanceResponse.from(
                service.cancel(request.toCommand(id), actor(authentication)));
    }

    @ExceptionHandler(MaintenanceValidationException.class)
    ResponseEntity<ProblemDetail> validation(MaintenanceValidationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "MAINTENANCE_VALIDATION_FAILED", ex.getMessage());
    }

    @ExceptionHandler(MaintenanceNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(MaintenanceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "MAINTENANCE_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(MaintenanceIncidentNotFoundException.class)
    ResponseEntity<ProblemDetail> incidentNotFound(MaintenanceIncidentNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "MAINTENANCE_INCIDENT_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler({MaintenanceVersionConflictException.class,
            EquipmentMaintenanceVersionConflictException.class})
    ResponseEntity<ProblemDetail> versionConflict(RuntimeException ex) {
        String code = ex instanceof MaintenanceVersionConflictException
                ? "MAINTENANCE_VERSION_CONFLICT"
                : "MAINTENANCE_EQUIPMENT_VERSION_CONFLICT";
        return problem(HttpStatus.CONFLICT, code, ex.getMessage());
    }

    @ExceptionHandler({MaintenanceStateConflictException.class,
            MaintenanceIncidentMismatchException.class,
            MaintenanceActiveOrderConflictException.class,
            EquipmentMaintenanceStateConflictException.class})
    ResponseEntity<ProblemDetail> stateConflict(RuntimeException ex) {
        return problem(HttpStatus.CONFLICT, "MAINTENANCE_STATE_CONFLICT", ex.getMessage());
    }

    @ExceptionHandler({MaintenanceEquipmentUnavailableException.class,
            EquipmentMaintenanceNotFoundException.class})
    ResponseEntity<ProblemDetail> equipmentUnavailable(RuntimeException ex) {
        HttpStatus status = ex.getMessage() != null && ex.getMessage().contains("does not exist")
                ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT;
        String code = status == HttpStatus.NOT_FOUND
                ? "MAINTENANCE_EQUIPMENT_NOT_FOUND"
                : "MAINTENANCE_EQUIPMENT_STATE_CONFLICT";
        return problem(status, code, ex.getMessage());
    }

    private static <E extends Enum<E>> E parseEnum(
            String value, Class<E> type, String label) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new MaintenanceValidationException(
                    "Unsupported " + label + ": " + value + ".");
        }
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String code, String detail) {
        return ResponseEntity.status(status)
                .body(ApiProblemFactory.create(status, code, detail));
    }

    private static AuthenticatedActor actor(Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof CoachGymUserPrincipal principal) {
            return principal.authenticatedActor();
        }
        throw new IllegalStateException("Authentication principal is missing or invalid.");
    }
}
