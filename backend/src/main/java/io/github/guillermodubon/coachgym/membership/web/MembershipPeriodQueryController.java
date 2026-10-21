package io.github.guillermodubon.coachgym.membership.web;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.membership.application.MembershipPeriodQueryApplicationService;
import io.github.guillermodubon.coachgym.membership.application.MembershipPeriodSearchQuery;
import io.github.guillermodubon.coachgym.membership.application.MembershipSortDirection;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only HTTP boundary for authorized persisted membership periods. */
@RestController
@RequestMapping("/api/v1/memberships/{membershipId}/periods")
@Tag(name = "Memberships", description = "Membership lifecycle and read-only history.")
@SecurityRequirement(name = "sessionCookie")
class MembershipPeriodQueryController {

    private final MembershipPeriodQueryApplicationService service;

    MembershipPeriodQueryController(MembershipPeriodQueryApplicationService service) {
        this.service = Objects.requireNonNull(service);
    }

    @GetMapping
    @Operation(
            summary = "List persisted periods of an authorized membership",
            description = "ADMIN and RECEPTIONIST may read immutable commercial period snapshots "
                    + "for a membership they can access in the active branch. Periods are ordered "
                    + "by period number; historical plan, promotion, price, dates and purchased "
                    + "coverage are returned without covered branch identifiers.")
    @ApiResponse(responseCode = "200", description = "Membership period page returned",
            content = @Content(schema = @Schema(implementation = MembershipPeriodPageResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid page, size or direction")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "ADMIN or RECEPTIONIST role required")
    @ApiResponse(responseCode = "404", description = "Membership is not available in this branch")
    @ApiResponse(responseCode = "409", description = "An active branch must be selected")
    @ApiResponse(responseCode = "500", description = "Membership history or coverage is unavailable")
    ResponseEntity<MembershipPeriodPageResponse> findPage(
            @PathVariable("membershipId") UUID membershipId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @Parameter(description = "ASC or DESC by persisted period number.")
            @RequestParam(defaultValue = "DESC") MembershipSortDirection direction,
            Authentication authentication) {
        MembershipPeriodSearchQuery query = new MembershipPeriodSearchQuery(page, size, direction);
        MembershipPeriodPageResponse body = MembershipPeriodPageResponse.from(
                service.findPage(membershipId, query, actor(authentication)));
        return noStore(body);
    }

    @GetMapping("/{periodId}")
    @Operation(
            summary = "Read one persisted period of an authorized membership",
            description = "The requested period must belong to the membership in the path and to "
                    + "its authorized registration branch. A period identifier alone grants no access.")
    @ApiResponse(responseCode = "200", description = "Membership period returned",
            content = @Content(schema = @Schema(implementation = MembershipPeriodResponse.class)))
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "ADMIN or RECEPTIONIST role required")
    @ApiResponse(responseCode = "404", description = "Membership or period was not found")
    @ApiResponse(responseCode = "409", description = "An active branch must be selected")
    @ApiResponse(responseCode = "500", description = "Membership history or coverage is unavailable")
    ResponseEntity<MembershipPeriodResponse> findById(
            @PathVariable("membershipId") UUID membershipId,
            @PathVariable("periodId") UUID periodId,
            Authentication authentication) {
        var entry = service.findById(membershipId, periodId, actor(authentication));
        return noStore(MembershipPeriodResponse.from(entry.period(), entry.coverage()));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
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
