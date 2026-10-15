package io.github.guillermodubon.coachgym.client.web;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.client.ClientOperationalProfile;
import io.github.guillermodubon.coachgym.client.ClientPage;
import io.github.guillermodubon.coachgym.client.ClientSearchQuery;
import io.github.guillermodubon.coachgym.client.ClientSortDirection;
import io.github.guillermodubon.coachgym.client.ClientSortField;
import io.github.guillermodubon.coachgym.client.ClientStatus;
import io.github.guillermodubon.coachgym.client.ClientStatusHistoryDetails;
import io.github.guillermodubon.coachgym.client.application.ClientProfileApplicationService;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/clients")
@Tag(name = "Clients", description = "Client registration, searchable profiles, and lifecycle management.")
class ClientProfileController {

    private final ClientProfileApplicationService service;

    ClientProfileController(ClientProfileApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Search clients",
            description = "ADMIN and RECEPTIONIST can search the client catalog using allowlisted filters and stable pagination. An explicit branchId is available only to organization administrators for an active authorized branch; otherwise the active branch is required.",
            security = @SecurityRequirement(name = "sessionCookie"))
    @ApiResponse(responseCode = "200", description = "Client page returned")
    @ApiResponse(responseCode = "404", description = "Requested branch is not available")
    @ApiResponse(responseCode = "409", description = "No valid active branch is selected")
    ClientPage findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ClientStatus status,
            @RequestParam(required = false) String membershipStatus,
            @Parameter(description = "Optional branch UUID for organization administrators; must be an active branch the administrator is authorized to address. Otherwise the active branch is used.")
            @RequestParam(required = false) UUID branchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "LAST_NAME") ClientSortField sort,
            @RequestParam(defaultValue = "ASC") ClientSortDirection direction,
            Authentication authentication) {
        return service.findAll(new ClientSearchQuery(
                search, status, membershipStatus, page, size, sort, direction, branchId),
                actor(authentication));
    }

    @GetMapping("/{id}/profile")
    @Operation(
            summary = "Get an operational client profile",
            description = "ADMIN and RECEPTIONIST can view the client, current membership, payment summary, last access, emergency contact, and safe photo metadata.",
            security = @SecurityRequirement(name = "sessionCookie"))
    ClientOperationalProfile findProfile(
            @PathVariable UUID id,
            Authentication authentication) {
        return service.findProfile(id, actor(authentication));
    }

    @GetMapping("/{id}/status-history")
    @Operation(
            summary = "Get client status history",
            security = @SecurityRequirement(name = "sessionCookie"))
    List<ClientStatusHistoryDetails> findStatusHistory(
            @PathVariable UUID id,
            Authentication authentication) {
        return service.findStatusHistory(id, actor(authentication));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update a client profile",
            description = "ADMIN and RECEPTIONIST can update mutable profile and emergency-contact fields. Requires CSRF and optimistic locking.",
            security = @SecurityRequirement(name = "sessionCookie"))
    ClientOperationalProfile update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClientProfileRequest request,
            Authentication authentication) {
        return service.update(id, request.toCommand(), actor(authentication));
    }

    @PostMapping("/{id}/deactivate")
    @Operation(
            summary = "Deactivate a client",
            description = "ADMIN only. Requires CSRF, a reason, and the current optimistic-lock version. Memberships and payments are not modified.",
            security = @SecurityRequirement(name = "sessionCookie"))
    ClientOperationalProfile deactivate(
            @PathVariable UUID id,
            @Valid @RequestBody ClientLifecycleRequest request,
            Authentication authentication) {
        return service.deactivate(
                id, request.toDeactivateCommand(), actor(authentication));
    }

    @PostMapping("/{id}/reactivate")
    @Operation(
            summary = "Reactivate a client",
            description = "ADMIN only. Requires CSRF, a reason, and the current optimistic-lock version. Membership state is not changed.",
            security = @SecurityRequirement(name = "sessionCookie"))
    ClientOperationalProfile reactivate(
            @PathVariable UUID id,
            @Valid @RequestBody ClientLifecycleRequest request,
            Authentication authentication) {
        return service.reactivate(
                id, request.toReactivateCommand(), actor(authentication));
    }

    private static AuthenticatedActor actor(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal()
                instanceof CoachGymUserPrincipal principal)) {
            throw new IllegalStateException(
                    "Authenticated staff principal is required.");
        }
        return principal.authenticatedActor();
    }
}
