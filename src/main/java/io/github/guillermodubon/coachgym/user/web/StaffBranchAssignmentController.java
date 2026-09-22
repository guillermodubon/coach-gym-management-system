package io.github.guillermodubon.coachgym.user.web;

import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.AuthenticatedActorProvider;
import io.github.guillermodubon.coachgym.user.ChangeStaffScopeCommand;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentStatus;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentApplicationService;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentSearchPage;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentSearchQuery;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentSortDirection;
import io.github.guillermodubon.coachgym.user.application.StaffBranchAssignmentSortField;
import io.github.guillermodubon.coachgym.user.StaffScopeDetails;
import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Secure administrative HTTP boundary for staff scope and assignment lifecycle. */
@RestController
@RequestMapping("/api/v1/staff")
@Tag(name = "Staff branch administration", description =
        "Organization-administrator management of staff scope and branch assignments.")
@SecurityRequirement(name = "sessionCookie")
class StaffBranchAssignmentController {

    private final StaffBranchAssignmentApplicationService service;

    StaffBranchAssignmentController(StaffBranchAssignmentApplicationService service) {
        this.service = service;
    }

    @GetMapping("/branch-assignments")
    @Operation(
            summary = "List staff branch assignments",
            description = "Organization-scoped ADMIN only. Returns bounded, paginated assignment "
                    + "history using allowlisted filters and sorting. Branch ADMIN and RECEPTIONIST "
                    + "cannot administer assignments. Requires the server-side scope policy.")
    @ApiResponse(responseCode = "200", description = "Assignment page returned",
            content = @Content(schema = @Schema(implementation = StaffBranchAssignmentPageResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid filter, date range, pagination or sort")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Organization-scoped ADMIN required")
    StaffBranchAssignmentPageResponse findAssignments(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) RoleCode role,
            @RequestParam(required = false, name = "scope") StaffScopeType scopeType,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) StaffBranchAssignmentStatus status,
            @RequestParam(required = false) String assignedFrom,
            @RequestParam(required = false) String assignedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @Parameter(description = "ASSIGNED_AT, ENDED_AT, STATUS, STAFF_IDENTIFIER or BRANCH_CODE")
            @RequestParam(defaultValue = "ASSIGNED_AT") String sort,
            @RequestParam(defaultValue = "DESC") String direction,
            Authentication authentication) {
        StaffBranchAssignmentSearchPage result = service.findAssignments(
                new StaffBranchAssignmentSearchQuery(
                        userId, search, role, scopeType, branchId, status,
                        parseInstant(assignedFrom, "assignedFrom"),
                        parseInstant(assignedTo, "assignedTo"),
                        page, size, parseSort(sort), parseDirection(direction)),
                actor(authentication));
        return StaffBranchAssignmentPageResponse.from(result);
    }

    @GetMapping("/{userId}/branch-assignments")
    @Operation(
            summary = "Get staff assignment history",
            description = "Organization-scoped ADMIN only. The user identifier scopes the query "
                    + "server-side; role and status cannot be changed through this endpoint.")
    @ApiResponse(responseCode = "200", description = "Assignment history returned")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Organization-scoped ADMIN required")
    StaffBranchAssignmentPageResponse findUserAssignments(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "ASSIGNED_AT") String sort,
            @RequestParam(defaultValue = "DESC") String direction,
            Authentication authentication) {
        StaffBranchAssignmentSearchQuery query = new StaffBranchAssignmentSearchQuery(
                userId, null, null, null, null, null, null, null,
                page, size, parseSort(sort), parseDirection(direction));
        return StaffBranchAssignmentPageResponse.from(
                service.findAssignmentsForUser(userId, query, actor(authentication)));
    }

    @PostMapping("/branch-assignments")
    @Operation(
            summary = "Assign staff to a branch",
            description = "Organization-scoped ADMIN only. The target role, assignment status, "
                    + "organization, actor and timestamps remain server controlled. The branch "
                    + "must be active and the operation requires CSRF.")
    @ApiResponse(responseCode = "201", description = "Assignment created")
    @ApiResponse(responseCode = "400", description = "Invalid assignment request")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Organization-scoped ADMIN or CSRF required")
    @ApiResponse(responseCode = "409", description = "Duplicate, inactive branch or lifecycle conflict")
    ResponseEntity<StaffBranchAssignmentResponse> assign(
            @Valid @RequestBody StaffBranchAssignmentRequest request,
            Authentication authentication) {
        StaffBranchAssignmentDetails assigned = service.assign(
                request.toCommand(), actor(authentication));
        return ResponseEntity.status(201).body(StaffBranchAssignmentResponse.from(assigned));
    }

    @PostMapping("/branch-assignments/{assignmentId}/end")
    @Operation(
            summary = "End a staff branch assignment",
            description = "Organization-scoped ADMIN only. Requires an expected assignment "
                    + "version, bounded reason and CSRF. History is retained and never deleted.")
    @ApiResponse(responseCode = "200", description = "Assignment ended")
    @ApiResponse(responseCode = "400", description = "Invalid lifecycle request")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Organization-scoped ADMIN or CSRF required")
    @ApiResponse(responseCode = "404", description = "Assignment not found")
    @ApiResponse(responseCode = "409", description = "Stale version or lifecycle conflict")
    StaffBranchAssignmentResponse end(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody EndStaffBranchAssignmentRequest request,
            Authentication authentication) {
        return StaffBranchAssignmentResponse.from(
                service.end(request.toCommand(assignmentId), actor(authentication)));
    }

    @GetMapping("/{userId}/scope")
    @Operation(
            summary = "Read staff organizational scope",
            description = "Organization-scoped ADMIN only. Scope is separate from the ADMIN or "
                    + "RECEPTIONIST role and no permission map is returned.")
    @ApiResponse(responseCode = "200", description = "Scope returned")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Organization-scoped ADMIN required")
    @ApiResponse(responseCode = "404", description = "Staff scope not found")
    StaffScopeResponse findScope(
            @PathVariable UUID userId,
            Authentication authentication) {
        StaffScopeDetails scope = service.findScope(userId, actor(authentication));
        return StaffScopeResponse.from(scope);
    }

    @PutMapping("/{userId}/scope")
    @Operation(
            summary = "Change staff organizational scope",
            description = "Organization-scoped ADMIN only. Requires an expected scope version, "
                    + "bounded reason and CSRF. Self-modification, unsupported role/scope "
                    + "combinations and last-ADMIN lockout are rejected.")
    @ApiResponse(responseCode = "200", description = "Scope changed")
    @ApiResponse(responseCode = "400", description = "Invalid scope request")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Organization-scoped ADMIN or CSRF required")
    @ApiResponse(responseCode = "404", description = "Staff scope not found")
    @ApiResponse(responseCode = "409", description = "Stale version or anti-lockout conflict")
    StaffScopeResponse changeScope(
            @PathVariable UUID userId,
            @Valid @RequestBody ChangeStaffScopeRequest request,
            Authentication authentication) {
        ChangeStaffScopeCommand command = request.toCommand(userId);
        return StaffScopeResponse.from(service.changeScope(command, actor(authentication)));
    }

    private static AuthenticatedActor actor(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof AuthenticatedActorProvider provider)) {
            throw new IllegalStateException("Authenticated staff principal is required.");
        }
        return provider.authenticatedActor();
    }

    private static Instant parseInstant(String value, String parameter) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Invalid " + parameter + " timestamp.");
        }
    }

    private static StaffBranchAssignmentSortField parseSort(String value) {
        return parseEnum(value, StaffBranchAssignmentSortField.class, "sort");
    }

    private static StaffBranchAssignmentSortDirection parseDirection(String value) {
        return parseEnum(value, StaffBranchAssignmentSortDirection.class, "direction");
    }

    private static <T extends Enum<T>> T parseEnum(
            String value,
            Class<T> type,
            String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Assignment " + field + " is required.");
        }
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported assignment " + field + " value.");
        }
    }
}
