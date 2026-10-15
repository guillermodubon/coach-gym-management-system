package io.github.guillermodubon.coachgym.user.web;

import io.github.guillermodubon.coachgym.user.ActiveBranchContextManager;
import io.github.guillermodubon.coachgym.user.ActiveBranchContextResolver;
import io.github.guillermodubon.coachgym.user.AuthenticatedActorProvider;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.StaffBranchContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP boundary for the authenticated user's server-side branch preference. */
@RestController
@RequestMapping("/api/v1/me/branch-context")
@Tag(name = "Active branch context", description =
        "Server-validated branch selection for the authenticated staff session.")
@SecurityRequirement(name = "sessionCookie")
class StaffBranchContextController {

    private final ActiveBranchContextResolver resolver;
    private final ActiveBranchContextManager manager;

    StaffBranchContextController(
            ActiveBranchContextResolver resolver,
            ActiveBranchContextManager manager) {
        this.resolver = Objects.requireNonNull(resolver);
        this.manager = Objects.requireNonNull(manager);
    }

    @GetMapping
    @Operation(
            summary = "Get the authenticated branch context",
            description = "Returns the server-authoritative organization scope, current active "
                    + "branch and active branches available to the authenticated ADMIN or "
                    + "RECEPTIONIST. The selected branch never grants authorization by itself.")
    @ApiResponse(responseCode = "200", description = "Branch context returned",
            content = @Content(schema = @Schema(implementation = StaffBranchContextResponse.class)))
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "409", description = "Selected branch is no longer available")
    StaffBranchContextResponse get(Authentication authentication) {
        return StaffBranchContextResponse.from(resolver.resolve(actor(authentication).id()));
    }

    @PutMapping
    @Operation(
            summary = "Select an active branch",
            description = "Validates the branch against current active assignments and branch "
                    + "status before storing only its ID in the server-side session. Requires CSRF; "
                    + "the client-supplied ID is never trusted as authorization.")
    @ApiResponse(responseCode = "200", description = "Branch context selected")
    @ApiResponse(responseCode = "400", description = "Invalid branch selection")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Valid CSRF token required")
    @ApiResponse(responseCode = "409", description = "Branch is inactive or unauthorized")
    StaffBranchContextResponse select(
            @Valid @RequestBody SelectActiveBranchRequest request,
            Authentication authentication) {
        AuthenticatedActor actor = actor(authentication);
        return StaffBranchContextResponse.from(manager.select(actor.id(), request.toCommand()));
    }

    @DeleteMapping
    @Operation(
            summary = "Clear the active branch",
            description = "Clears only the server-side branch preference. It does not revoke any "
                    + "assignment and requires CSRF.")
    @ApiResponse(responseCode = "204", description = "Active branch cleared")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Valid CSRF token required")
    ResponseEntity<Void> clear(Authentication authentication) {
        manager.clear(actor(authentication).id());
        return ResponseEntity.noContent().build();
    }

    private static AuthenticatedActor actor(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof AuthenticatedActorProvider provider)) {
            throw new IllegalStateException("Authenticated staff principal is required.");
        }
        return provider.authenticatedActor();
    }
}
