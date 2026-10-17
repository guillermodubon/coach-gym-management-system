package io.github.guillermodubon.coachgym.organization.web;

import io.github.guillermodubon.coachgym.organization.OrganizationDetails;
import io.github.guillermodubon.coachgym.organization.OrganizationSummary;
import io.github.guillermodubon.coachgym.organization.application.OrganizationApplicationService;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.AuthenticatedActorProvider;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP boundary for the single canonical organization. */
@RestController
@RequestMapping("/api/v1/organization")
@Tag(name = "Organization", description = "Canonical Coach Gym organization administration.")
@SecurityRequirement(name = "sessionCookie")
class OrganizationController {

    private final OrganizationApplicationService service;

    OrganizationController(OrganizationApplicationService service) {
        this.service = Objects.requireNonNull(service);
    }

    @GetMapping
    @Operation(
            summary = "Get the canonical organization",
            description = "Returns the one server-selected organization. ADMIN receives the "
                    + "administrative detail projection; RECEPTIONIST receives the safe summary. "
                    + "No organization identifier or tenant selector is accepted.")
    @ApiResponse(responseCode = "200", description = "Organization returned",
            content = @Content(schema = @Schema(implementation = OrganizationResponse.class)))
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "The authenticated role is not allowed")
    @ApiResponse(responseCode = "404", description = "Canonical organization not found")
    ResponseEntity<OrganizationResponse> find(Authentication authentication) {
        if (isAdmin(authentication)) {
            OrganizationDetails details = service.findCanonical();
            return ResponseEntity.ok(OrganizationResponse.from(details));
        }
        OrganizationSummary summary = service.findCanonicalSummary();
        return ResponseEntity.ok(OrganizationResponse.from(summary));
    }

    @PutMapping
    @Operation(
            summary = "Update the canonical organization",
            description = "ADMIN only. Updates the allowlisted identity, contact, timezone and "
                    + "currency fields with an expected optimistic-lock version. Code, status, "
                    + "actor and timestamps are server controlled. A valid CSRF token is required.")
    @ApiResponse(responseCode = "200", description = "Organization updated",
            content = @Content(schema = @Schema(implementation = OrganizationResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid organization request")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "ADMIN role or valid CSRF token required")
    @ApiResponse(responseCode = "404", description = "Canonical organization not found")
    @ApiResponse(responseCode = "409", description = "Version or lifecycle conflict")
    OrganizationResponse update(
            @Valid @RequestBody UpdateOrganizationRequest request,
            Authentication authentication) {
        return OrganizationResponse.from(
                service.update(request.toCommand(), actor(authentication)));
    }

    private static AuthenticatedActor actor(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof AuthenticatedActorProvider provider)) {
            throw new IllegalStateException("Authenticated staff principal is required.");
        }
        return provider.authenticatedActor();
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
