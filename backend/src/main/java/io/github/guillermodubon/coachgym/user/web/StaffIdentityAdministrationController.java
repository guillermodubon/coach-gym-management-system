package io.github.guillermodubon.coachgym.user.web;

import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.AuthenticatedActorProvider;
import io.github.guillermodubon.coachgym.user.StaffIdentityStatus;
import io.github.guillermodubon.coachgym.user.application.StaffIdentityAdministrationApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Organization-admin-only state and authority changes for existing staff accounts. */
@RestController
@RequestMapping("/api/v1/staff")
@Tag(name = "Staff identity administration", description =
        "Organization-scoped ADMIN lifecycle operations; branch admins cannot administer identities. "
                + "Suspension retains assignments, while deactivation is terminal and closes active assignments. "
                + "Security-version changes invalidate prior sessions when they make their next request.")
@SecurityRequirement(name = "sessionCookie")
class StaffIdentityAdministrationController {

    private final StaffIdentityAdministrationApplicationService service;

    StaffIdentityAdministrationController(StaffIdentityAdministrationApplicationService service) {
        this.service = service;
    }

    @PostMapping("/{userId}/suspend")
    @Operation(summary = "Suspend a staff account", description =
            "Organization-scoped ADMIN only. Self-suspension and removal of the last organization ADMIN are prohibited. "
                    + "Suspension retains branch assignments. Suspending an organization ADMIN requires current-password reauthentication and CSRF.")
    @ApiResponse(responseCode = "200", description = "Account suspended")
    @ApiResponse(responseCode = "403", description = "Organization ADMIN or reauthentication required")
    @ApiResponse(responseCode = "409", description = "Self-operation, stale version, or last-admin conflict")
    StaffIdentityAdministrationResponse suspend(
            @PathVariable UUID userId,
            @Valid @RequestBody StaffIdentityStatusRequest request,
            Authentication authentication) {
        return response(service.changeStatus(
                request.toCommand(userId, StaffIdentityStatus.SUSPENDED),
                actor(authentication), request.currentPassword()));
    }

    @PostMapping("/{userId}/reactivate")
    @Operation(summary = "Reactivate a staff account", description =
            "Organization-scoped ADMIN only. Branch-scoped accounts require an active branch assignment. Requires CSRF.")
    @ApiResponse(responseCode = "200", description = "Account reactivated")
    @ApiResponse(responseCode = "403", description = "Organization ADMIN required")
    @ApiResponse(responseCode = "409", description = "Lifecycle or branch-assignment conflict")
    StaffIdentityAdministrationResponse reactivate(
            @PathVariable UUID userId,
            @Valid @RequestBody StaffIdentityStatusRequest request,
            Authentication authentication) {
        return response(service.changeStatus(
                request.toCommand(userId, StaffIdentityStatus.ACTIVE),
                actor(authentication), request.currentPassword()));
    }

    @PostMapping("/{userId}/deactivate")
    @Operation(summary = "Deactivate a staff account", description =
            "Organization-scoped ADMIN only. Deactivation is terminal and closes active branch assignments. "
                    + "Self-deactivation and last-admin removal are prohibited; deactivating an organization ADMIN requires reauthentication and CSRF.")
    @ApiResponse(responseCode = "200", description = "Account deactivated")
    @ApiResponse(responseCode = "403", description = "Organization ADMIN or reauthentication required")
    @ApiResponse(responseCode = "409", description = "Self-operation, stale version, or last-admin conflict")
    StaffIdentityAdministrationResponse deactivate(
            @PathVariable UUID userId,
            @Valid @RequestBody StaffIdentityStatusRequest request,
            Authentication authentication) {
        return response(service.changeStatus(
                request.toCommand(userId, StaffIdentityStatus.DEACTIVATED),
                actor(authentication), request.currentPassword()));
    }

    @PutMapping("/{userId}/role-scope")
    @Operation(summary = "Change staff role and scope", description =
            "Organization-scoped ADMIN only. Requires current-password reauthentication and CSRF. "
                    + "The supported combinations are ADMIN+ORGANIZATION, ADMIN+BRANCH, and RECEPTIONIST+BRANCH. "
                    + "Self-promotion/demotion and last organization-admin removal are prohibited.")
    @ApiResponse(responseCode = "200", description = "Authority changed")
    @ApiResponse(responseCode = "403", description = "Organization ADMIN or reauthentication required")
    @ApiResponse(responseCode = "409", description = "Stale version, invalid combination, or last-admin conflict")
    StaffIdentityAdministrationResponse changeRoleScope(
            @PathVariable UUID userId,
            @Valid @RequestBody ChangeStaffRoleScopeRequest request,
            Authentication authentication) {
        return response(service.changeRoleScope(
                request.toCommand(userId), actor(authentication), request.currentPassword()));
    }

    private static StaffIdentityAdministrationResponse response(
            io.github.guillermodubon.coachgym.user.application.StaffIdentityAdministrationState state) {
        return StaffIdentityAdministrationResponse.from(state);
    }

    private static AuthenticatedActor actor(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof AuthenticatedActorProvider provider)) {
            throw new IllegalStateException("Authenticated staff principal is required.");
        }
        return provider.authenticatedActor();
    }
}
