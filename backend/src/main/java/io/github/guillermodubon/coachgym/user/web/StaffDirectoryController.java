package io.github.guillermodubon.coachgym.user.web;

import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.AuthenticatedActorProvider;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import io.github.guillermodubon.coachgym.user.application.StaffDirectoryApplicationService;
import io.github.guillermodubon.coachgym.user.application.StaffDirectorySearchQuery;
import io.github.guillermodubon.coachgym.user.application.StaffDirectorySortDirection;
import io.github.guillermodubon.coachgym.user.application.StaffDirectorySortField;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

/** Read-only organization-admin directory and detail for provisioned staff identities. */
@RestController
@RequestMapping("/api/v1/staff")
@Tag(name = "Staff directory", description =
        "Administrative read-only directory for provisioned staff accounts. Pending invitations are not accounts.")
@SecurityRequirement(name = "sessionCookie")
class StaffDirectoryController {

    private final StaffDirectoryApplicationService service;

    StaffDirectoryController(StaffDirectoryApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Search the staff directory", description =
            "Organization-scoped ADMIN only. Searches real staff accounts in every persisted status. "
                    + "Email is searchable but omitted from directory rows. Results are paged and never include invitations.")
    @ApiResponse(responseCode = "200", description = "A page of staff accounts")
    @ApiResponse(responseCode = "400", description = "Invalid filter, sort, or page bounds")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Organization ADMIN required")
    @ApiResponse(responseCode = "500", description = "Directory temporarily unavailable")
    ResponseEntity<StaffDirectoryPageResponse> findPage(
            @Parameter(description = "Case-insensitive literal search across names, username, and email; maximum 120 characters")
            @RequestParam(required = false) String search,
            @Parameter(description = "Persisted account status")
            @RequestParam(required = false) StaffAccountStatus status,
            @Parameter(description = "Persisted staff role")
            @RequestParam(required = false) RoleCode role,
            @Parameter(description = "Persisted organization scope")
            @RequestParam(required = false) StaffScopeType scopeType,
            @Parameter(description = "Return staff with an active assignment to this branch")
            @RequestParam(required = false) UUID branchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @Parameter(description = "LAST_NAME, USERNAME, or CREATED_AT")
            @RequestParam(required = false) StaffDirectorySortField sort,
            @Parameter(description = "ASC or DESC")
            @RequestParam(required = false) StaffDirectorySortDirection direction,
            Authentication authentication) {
        StaffDirectorySearchQuery query = new StaffDirectorySearchQuery(
                search, status, role, scopeType, branchId, page, size, sort, direction);
        StaffDirectoryPageResponse body = StaffDirectoryPageResponse.from(
                service.findPage(query, actor(authentication)));
        return noStore(body);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Read staff account details", description =
            "Organization-scoped ADMIN only. Returns account identity, contact address, status, authority, "
                    + "and the current security and scope versions. Credential data, sessions, photos, and assignments are excluded.")
    @ApiResponse(responseCode = "200", description = "Staff account details")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Organization ADMIN required")
    @ApiResponse(responseCode = "404", description = "Staff account not found")
    @ApiResponse(responseCode = "500", description = "Directory temporarily unavailable")
    ResponseEntity<StaffDirectoryDetailsResponse> findDetails(
            @PathVariable UUID userId,
            Authentication authentication) {
        StaffDirectoryDetailsResponse body = StaffDirectoryDetailsResponse.from(
                service.findDetails(userId, actor(authentication)));
        return noStore(body);
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
                || !(authentication.getPrincipal() instanceof AuthenticatedActorProvider provider)) {
            throw new IllegalStateException("Authenticated staff principal is required.");
        }
        return provider.authenticatedActor();
    }
}
