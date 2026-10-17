package io.github.guillermodubon.coachgym.user.web;

import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.AuthenticatedActorProvider;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffInvitationStatus;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import io.github.guillermodubon.coachgym.user.application.StaffInvitationApplicationService;
import io.github.guillermodubon.coachgym.user.application.StaffInvitationAcceptanceApplicationService;
import io.github.guillermodubon.coachgym.user.application.StaffInvitationSearchQuery;
import io.github.guillermodubon.coachgym.user.application.StaffInvitationSortDirection;
import io.github.guillermodubon.coachgym.user.application.StaffInvitationSortField;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP boundary for private staff invitations and token-protected acceptance. */
@RestController
@RequestMapping("/api/v1/staff-invitations")
@Tag(name = "Staff invitations", description =
        "Private invitation-only staff provisioning; public registration is unavailable. "
                + "Invitation states are PENDING, ACCEPTED, EXPIRED, and REVOKED. "
                + "ADMIN invitations expire after 24 hours and RECEPTIONIST invitations after 48 hours. "
                + "Resends rotate the one-time link and are abuse-limited.")
class StaffInvitationController {

    private final StaffInvitationApplicationService invitations;
    private final StaffInvitationAcceptanceApplicationService acceptance;

    StaffInvitationController(
            StaffInvitationApplicationService invitations,
            StaffInvitationAcceptanceApplicationService acceptance) {
        this.invitations = invitations;
        this.acceptance = acceptance;
    }

    @PostMapping
    @Operation(summary = "Create a staff invitation", description =
            "Organization-scoped ADMIN only. Invitees have no account until acceptance. "
                    + "Allowed combinations are ADMIN+ORGANIZATION, ADMIN+BRANCH, and RECEPTIONIST+BRANCH; "
                    + "RECEPTIONIST+ORGANIZATION is prohibited. Inviting an ADMIN requires current-password "
                    + "reauthentication. Requires CSRF.")
    @SecurityRequirement(name = "sessionCookie")
    @ApiResponse(responseCode = "201", description = "Invitation created; email is masked in the response")
    @ApiResponse(responseCode = "403", description = "Organization ADMIN or reauthentication required")
    @ApiResponse(responseCode = "409", description = "Duplicate or policy conflict")
    @ApiResponse(responseCode = "429", description = "Invitation abuse limit reached")
    ResponseEntity<StaffInvitationResponse> create(
            @Valid @RequestBody CreateStaffInvitationRequest request,
            Authentication authentication) {
        return noStore(HttpStatus.CREATED, StaffInvitationResponse.from(
                invitations.create(request.toCommand(), actor(authentication), request.currentPassword())));
    }

    @GetMapping
    @Operation(summary = "Search staff invitations", description =
            "Organization-scoped ADMIN only. Supports bounded pagination and allowlisted filters "
                    + "for status, role, scope, branch, masked-safe email search, creation/expiration "
                    + "ranges and sort. Requires the server-side organization scope policy.")
    @SecurityRequirement(name = "sessionCookie")
    @ApiResponse(responseCode = "200", description = "Bounded invitation page returned")
    @ApiResponse(responseCode = "400", description = "Invalid date range, filter, page, size or sort")
    @ApiResponse(responseCode = "403", description = "Organization-scoped ADMIN required")
    StaffInvitationPageResponse findPage(
            @RequestParam(required = false) StaffInvitationStatus status,
            @RequestParam(required = false) RoleCode role,
            @RequestParam(required = false, name = "scope") StaffScopeType scopeType,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo,
            @RequestParam(required = false) String expiresFrom,
            @RequestParam(required = false) String expiresTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @Parameter(description = "CREATED_AT, EXPIRES_AT, STATUS, or PROPOSED_ROLE")
            @RequestParam(defaultValue = "CREATED_AT") StaffInvitationSortField sort,
            @RequestParam(defaultValue = "DESC") StaffInvitationSortDirection direction,
            Authentication authentication) {
        StaffInvitationSearchQuery query = new StaffInvitationSearchQuery(
                status, role, scopeType, branchId, email,
                parseInstant(createdFrom, "createdFrom"),
                parseInstant(createdTo, "createdTo"),
                parseInstant(expiresFrom, "expiresFrom"),
                parseInstant(expiresTo, "expiresTo"),
                page, size, sort, direction);
        return StaffInvitationPageResponse.from(
                invitations.findPage(query, actor(authentication)), page, size);
    }

    @GetMapping("/{invitationId}")
    @Operation(summary = "Get invitation details", description =
            "Organization-scoped ADMIN only. The email is masked; no token or credential material is returned.")
    @SecurityRequirement(name = "sessionCookie")
    @ApiResponse(responseCode = "200", description = "Invitation details returned")
    @ApiResponse(responseCode = "404", description = "Invitation not found in the administrator's organization")
    ResponseEntity<StaffInvitationResponse> findById(
            @PathVariable UUID invitationId,
            Authentication authentication) {
        return noStore(HttpStatus.OK, StaffInvitationResponse.from(
                invitations.findById(invitationId, actor(authentication))));
    }

    @PostMapping("/{invitationId}/resend")
    @Operation(summary = "Resend a pending invitation", description =
            "Organization-scoped ADMIN only. Rotates the one-time token and invalidates the previous link. Requires CSRF.")
    @SecurityRequirement(name = "sessionCookie")
    @ApiResponse(responseCode = "200", description = "Delivery result returned without token material")
    @ApiResponse(responseCode = "409", description = "Invitation is no longer pending or version is stale")
    @ApiResponse(responseCode = "429", description = "Invitation delivery limit reached")
    ResponseEntity<StaffInvitationResponse> resend(
            @PathVariable UUID invitationId,
            @Valid @RequestBody InvitationVersionRequest request,
            Authentication authentication) {
        return noStore(HttpStatus.OK, StaffInvitationResponse.from(
                invitations.resend(invitationId, request.expectedVersion(), actor(authentication))));
    }

    @PostMapping("/{invitationId}/revoke")
    @Operation(summary = "Revoke a pending invitation", description =
            "Organization-scoped ADMIN only. Immediately invalidates the one-time token and requires CSRF.")
    @SecurityRequirement(name = "sessionCookie")
    @ApiResponse(responseCode = "200", description = "Invitation revoked")
    @ApiResponse(responseCode = "409", description = "Invitation is no longer pending or version is stale")
    ResponseEntity<StaffInvitationResponse> revoke(
            @PathVariable UUID invitationId,
            @Valid @RequestBody InvitationVersionRequest request,
            Authentication authentication) {
        return noStore(HttpStatus.OK, StaffInvitationResponse.from(
                invitations.revoke(invitationId, request.expectedVersion(), actor(authentication))));
    }

    @PostMapping("/inspect")
    @Operation(summary = "Inspect an invitation link", description =
            "Public token-protected operation. Returns only a masked email, approved role/scope, branch summaries and expiry. "
                    + "Invalid, expired, revoked, unavailable, or abuse-limited attempts share one safe conflict response. "
                    + "Requires CSRF.")
    @ApiResponse(responseCode = "200", description = "Safe invitation preview returned")
    @ApiResponse(responseCode = "409", description = "Invitation is not available")
    ResponseEntity<?> inspect(
            @Valid @RequestBody InspectStaffInvitationRequest request,
            HttpServletRequest servletRequest) {
        return noStore(HttpStatus.OK, acceptance.inspect(
                request.token(), servletRequest.getRemoteAddr()));
    }

    @PostMapping("/accept")
    @Operation(summary = "Accept a staff invitation", description =
            "Public token-protected provisioning. The request allowlist is token, password, confirmation and profile names; "
                    + "passwords must be 12–256 characters. Email, role, scope, branches, status, permissions and actor identity "
                    + "are invitation/server controlled. Invalid, expired, revoked, unavailable, or abuse-limited attempts "
                    + "share one safe conflict response. Requires CSRF.")
    @ApiResponse(responseCode = "201", description = "Staff account provisioned")
    @ApiResponse(responseCode = "409", description = "Invitation is not available")
    ResponseEntity<StaffInvitationAcceptanceResponse> accept(
            @Valid @RequestBody AcceptStaffInvitationRequest request,
            HttpServletRequest servletRequest) {
        return noStore(HttpStatus.CREATED, StaffInvitationAcceptanceResponse.from(
                acceptance.accept(request.toCommand(), servletRequest.getRemoteAddr())));
    }

    private static AuthenticatedActor actor(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof AuthenticatedActorProvider provider)) {
            throw new IllegalStateException("Authenticated staff principal is required.");
        }
        return provider.authenticatedActor();
    }

    private static Instant parseInstant(String value, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException invalid) {
            throw new IllegalArgumentException("Invalid invitation " + name + " timestamp.");
        }
    }

    private static <T> ResponseEntity<T> noStore(HttpStatus status, T body) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("Referrer-Policy", "no-referrer")
                .body(body);
    }
}
