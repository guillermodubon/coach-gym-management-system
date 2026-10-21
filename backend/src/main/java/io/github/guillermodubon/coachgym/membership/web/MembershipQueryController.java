package io.github.guillermodubon.coachgym.membership.web;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.membership.application.MembershipQueryApplicationService;
import io.github.guillermodubon.coachgym.membership.application.MembershipSearchQuery;
import io.github.guillermodubon.coachgym.membership.application.MembershipSortDirection;
import io.github.guillermodubon.coachgym.membership.application.MembershipSortField;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only HTTP boundary for the authorized membership directory. */
@RestController
@RequestMapping("/api/v1/memberships")
@Tag(name = "Memberships", description = "Membership lifecycle and read-only search.")
@SecurityRequirement(name = "sessionCookie")
class MembershipQueryController {

    private final MembershipQueryApplicationService service;

    MembershipQueryController(MembershipQueryApplicationService service) {
        this.service = Objects.requireNonNull(service);
    }

    @GetMapping
    @Operation(
            summary = "List memberships in the authorized active branch",
            description = "ADMIN and RECEPTIONIST may search membership codes and filter by client, "
                    + "status and branch. Results and totals are restricted to the branch resolved "
                    + "from the authenticated staff context. An organization ADMIN may explicitly "
                    + "select another active branch available to that account; omitting branchId "
                    + "never requests all branches. Client names and contact details are not returned.")
    @ApiResponse(responseCode = "200", description = "Membership page returned",
            content = @Content(schema = @Schema(implementation = MembershipPageResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid search, filter, page or sort value")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "ADMIN or RECEPTIONIST role required")
    @ApiResponse(responseCode = "404", description = "Explicit branch is not available to the actor")
    @ApiResponse(responseCode = "409", description = "An active branch must be selected")
    @ApiResponse(responseCode = "500", description = "Membership or historical coverage query unavailable")
    ResponseEntity<MembershipPageResponse> findPage(
            @Parameter(description = "Case-insensitive literal search over membership code only; max 120 characters.")
            @RequestParam(required = false) String search,
            @Parameter(description = "Limit results to this client record within the authorized branch.")
            @RequestParam(required = false) UUID clientId,
            @Parameter(description = "ACTIVE, FROZEN, EXPIRED or CANCELLED; omitted includes every status.")
            @RequestParam(required = false) MembershipStatus status,
            @Parameter(description = "Optional active branch filter subject to persisted staff scope and assignment.")
            @RequestParam(required = false) UUID branchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @Parameter(description = "CREATED_AT or MEMBERSHIP_CODE.")
            @RequestParam(defaultValue = "CREATED_AT") MembershipSortField sort,
            @Parameter(description = "ASC or DESC.")
            @RequestParam(defaultValue = "DESC") MembershipSortDirection direction,
            Authentication authentication) {
        MembershipSearchQuery query = new MembershipSearchQuery(
                search, clientId, status, branchId, page, size, sort, direction);
        MembershipPageResponse body = MembershipPageResponse.from(
                service.findPage(query, actor(authentication)));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("Referrer-Policy", "no-referrer")
                .body(body);
    }

    private static AuthenticatedActor actor(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof CoachGymUserPrincipal principal)) {
            throw new IllegalStateException("Authenticated staff principal is required.");
        }
        return principal.authenticatedActor();
    }
}
