package io.github.guillermodubon.coachgym.maintenance.web;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.equipment.EquipmentIncidentNotFoundException;
import io.github.guillermodubon.coachgym.equipment.EquipmentIncidentStateConflictException;
import io.github.guillermodubon.coachgym.equipment.EquipmentIncidentVersionConflictException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentNotFoundException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentStateConflictException;
import io.github.guillermodubon.coachgym.equipment.application.exception.EquipmentVersionConflictException;
import io.github.guillermodubon.coachgym.maintenance.IncidentPriority;
import io.github.guillermodubon.coachgym.maintenance.IncidentStatus;
import io.github.guillermodubon.coachgym.maintenance.application.IncidentApplicationService;
import io.github.guillermodubon.coachgym.maintenance.application.IncidentEquipmentUnavailableException;
import io.github.guillermodubon.coachgym.maintenance.application.IncidentNotFoundException;
import io.github.guillermodubon.coachgym.maintenance.application.IncidentSearchQuery;
import io.github.guillermodubon.coachgym.maintenance.application.IncidentSortDirection;
import io.github.guillermodubon.coachgym.maintenance.application.IncidentSortField;
import io.github.guillermodubon.coachgym.maintenance.application.IncidentStateConflictException;
import io.github.guillermodubon.coachgym.maintenance.application.IncidentVersionConflictException;
import io.github.guillermodubon.coachgym.maintenance.domain.IncidentValidationException;
import io.github.guillermodubon.coachgym.shared.web.ApiProblemFactory;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/incidents")
@Tag(name = "Equipment Incidents", description = "Equipment incident reporting and lifecycle management.")
@SecurityRequirement(name = "sessionCookie")
class IncidentController {

    private final IncidentApplicationService incidentService;

    IncidentController(IncidentApplicationService incidentService) {
        this.incidentService = incidentService;
    }

    @PostMapping
    @Operation(summary = "Report an equipment incident", description = """
            Reports an incident in OPEN status. ADMIN and RECEPTIONIST may report.
            State-changing requests require CSRF. When takeOutOfService is true,
            equipmentVersion is required and the equipment may transition to OUT_OF_SERVICE.
            """)
    @ApiResponse(responseCode = "201", description = "Incident reported")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Insufficient permission or invalid CSRF token")
    @ApiResponse(responseCode = "404", description = "Equipment not found")
    @ApiResponse(responseCode = "409", description = "Equipment state or version conflict")
    ResponseEntity<IncidentResponse> report(
            @Valid @RequestBody ReportIncidentRequest request,
            Authentication authentication) {
        IncidentResponse response = IncidentResponse.from(
                incidentService.report(
                        request.toCommand(), actor(authentication)));
        return ResponseEntity.created(
                        URI.create("/api/v1/incidents/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an incident", description = "ADMIN and RECEPTIONIST may query incidents.")
    IncidentResponse findById(@PathVariable UUID id) {
        return IncidentResponse.from(incidentService.findById(id));
    }

    @GetMapping
    @Operation(summary = "List incidents", description = "Returns a filtered and paginated incident list for ADMIN and RECEPTIONIST.")
    IncidentPageResponse findAll(
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Instant reportedFrom,
            @RequestParam(required = false) Instant reportedUntil,
            @RequestParam(required = false) UUID reportedByUserId,
            @RequestParam(required = false) UUID resolvedByUserId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "REPORTED_AT") String sort,
            @RequestParam(defaultValue = "DESC") String direction) {
        IncidentSearchQuery query = new IncidentSearchQuery(
                equipmentId,
                parseStatus(status),
                parsePriority(priority),
                reportedFrom,
                reportedUntil,
                reportedByUserId,
                resolvedByUserId,
                search,
                page,
                size,
                IncidentSortField.from(sort),
                IncidentSortDirection.from(direction));
        return IncidentPageResponse.from(incidentService.findAll(query));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Get incident status history", description = "Returns append-only lifecycle history for ADMIN and RECEPTIONIST.")
    List<IncidentStatusHistoryResponse> findStatusHistory(
            @PathVariable UUID id) {
        return incidentService.findStatusHistory(id).stream()
                .map(IncidentStatusHistoryResponse::from)
                .toList();
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Start incident investigation", description = "ADMIN only. Requires CSRF and the current optimistic-lock version.")
    IncidentResponse startInvestigation(
            @PathVariable UUID id,
            @Valid @RequestBody StartIncidentInvestigationRequest request,
            Authentication authentication) {
        return IncidentResponse.from(incidentService.startInvestigation(
                request.toCommand(id), actor(authentication)));
    }

    @PostMapping("/{id}/priority")
    @Operation(summary = "Change incident priority", description = "ADMIN only. Requires CSRF and the current optimistic-lock version.")
    IncidentResponse changePriority(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeIncidentPriorityRequest request,
            Authentication authentication) {
        return IncidentResponse.from(incidentService.changePriority(
                request.toCommand(id), actor(authentication)));
    }

    @PostMapping("/{id}/resolve")
    @Operation(summary = "Resolve an incident", description = "ADMIN only. Supports IN_PROGRESS to RESOLVED and requires CSRF and optimistic locking.")
    IncidentResponse resolve(
            @PathVariable UUID id,
            @Valid @RequestBody ResolveIncidentRequest request,
            Authentication authentication) {
        return IncidentResponse.from(incidentService.resolve(
                request.toCommand(id), actor(authentication)));
    }

    @ExceptionHandler(IncidentValidationException.class)
    ResponseEntity<ProblemDetail> handleValidation(
            IncidentValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST,
                "INCIDENT_VALIDATION_FAILED", exception.getMessage());
    }

    @ExceptionHandler(IncidentNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(
            IncidentNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND,
                "INCIDENT_NOT_FOUND", exception.getMessage());
    }


    @ExceptionHandler(IncidentEquipmentUnavailableException.class)
    ResponseEntity<ProblemDetail> handleEquipmentUnavailable(
            IncidentEquipmentUnavailableException exception) {
        HttpStatus status = exception.getMessage().contains("does not exist")
                ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT;
        String code = status == HttpStatus.NOT_FOUND
                ? "INCIDENT_EQUIPMENT_NOT_FOUND"
                : "INCIDENT_EQUIPMENT_RETIRED";
        return problem(status, code, exception.getMessage());
    }

    @ExceptionHandler(IncidentVersionConflictException.class)
    ResponseEntity<ProblemDetail> handleVersionConflict(
            IncidentVersionConflictException exception) {
        return problem(HttpStatus.CONFLICT,
                "INCIDENT_VERSION_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(IncidentStateConflictException.class)
    ResponseEntity<ProblemDetail> handleStateConflict(
            IncidentStateConflictException exception) {
        return problem(HttpStatus.CONFLICT,
                "INCIDENT_STATE_CONFLICT", exception.getMessage());
    }


    private static IncidentStatus parseStatus(String value) {
        return parseEnum(value, IncidentStatus.class, "incident status");
    }

    private static IncidentPriority parsePriority(String value) {
        return parseEnum(value, IncidentPriority.class, "incident priority");
    }

    private static <E extends Enum<E>> E parseEnum(
            String value, Class<E> enumType, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IncidentValidationException(
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
                && authentication.getPrincipal()
                instanceof CoachGymUserPrincipal principal) {
            return principal.authenticatedActor();
        }
        throw new IllegalStateException(
                "Authentication principal is missing or invalid.");
    }

    @ExceptionHandler(EquipmentIncidentNotFoundException.class)
    ResponseEntity<ProblemDetail> handleEquipmentNotFound(
            EquipmentIncidentNotFoundException exception) {

        return problem(
                HttpStatus.NOT_FOUND,
                "INCIDENT_EQUIPMENT_NOT_FOUND",
                "The equipment selected for the incident "
                        + "was not found.");
    }

    @ExceptionHandler(EquipmentIncidentVersionConflictException.class)
    ResponseEntity<ProblemDetail> handleEquipmentVersionConflict(
            EquipmentIncidentVersionConflictException exception) {

        return problem(
                HttpStatus.CONFLICT,
                "EQUIPMENT_VERSION_CONFLICT",
                "The equipment version is stale.");
    }

    @ExceptionHandler(EquipmentIncidentStateConflictException.class)
    ResponseEntity<ProblemDetail> handleEquipmentStateConflict(
            EquipmentIncidentStateConflictException exception) {

        return problem(
                HttpStatus.CONFLICT,
                "INCIDENT_EQUIPMENT_STATE_CONFLICT",
                exception.getMessage());
    }

}
