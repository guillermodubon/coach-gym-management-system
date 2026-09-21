package io.github.guillermodubon.coachgym.organization.web;

import io.github.guillermodubon.coachgym.organization.GymBranchStatus;
import io.github.guillermodubon.coachgym.organization.application.GymBranchApplicationService;
import io.github.guillermodubon.coachgym.organization.application.GymBranchPage;
import io.github.guillermodubon.coachgym.organization.application.GymBranchSearchQuery;
import io.github.guillermodubon.coachgym.organization.application.GymBranchSortDirection;
import io.github.guillermodubon.coachgym.organization.application.GymBranchSortField;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.AuthenticatedActorProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;
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
import org.springframework.web.util.UriComponentsBuilder;

/** HTTP boundary for the non-destructive canonical branch catalog and lifecycle. */
@RestController
@RequestMapping("/api/v1/branches")
@Tag(name = "Gym branches", description = "Canonical organization branch catalog and lifecycle.")
@SecurityRequirement(name = "sessionCookie")
class GymBranchController {

    private final GymBranchApplicationService service;

    GymBranchController(GymBranchApplicationService service) {
        this.service = Objects.requireNonNull(service);
    }

    @PostMapping
    @Operation(
            summary = "Create a gym branch",
            description = "ADMIN only. Creates an ACTIVE branch under the server-selected "
                    + "canonical organization. Organization, status, initial marker, actor and "
                    + "timestamps are server controlled. Requires CSRF.")
    @ApiResponse(responseCode = "201", description = "Branch created",
            content = @Content(schema = @Schema(implementation = BranchResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid branch request")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "ADMIN role or valid CSRF token required")
    @ApiResponse(responseCode = "404", description = "Canonical organization not found")
    @ApiResponse(responseCode = "409", description = "Branch code conflict")
    ResponseEntity<BranchResponse> create(
            @Valid @RequestBody CreateBranchRequest request,
            Authentication authentication,
            UriComponentsBuilder uriBuilder) {
        var created = service.create(request.toCommand(), actor(authentication));
        URI location = uriBuilder.path("/api/v1/branches/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(BranchResponse.from(created));
    }

    @GetMapping
    @Operation(
            summary = "List gym branches",
            description = "Returns a bounded, deterministic branch catalog. Filters are status, "
                    + "case-insensitive search over code/name/city, optional city and countryCode; "
                    + "sort is allowlisted and page size is limited to 100. ADMIN and RECEPTIONIST "
                    + "may read. No organization selector or branch scope is accepted.")
    @ApiResponse(responseCode = "200", description = "Branch page returned",
            content = @Content(schema = @Schema(implementation = BranchPageResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid filter, pagination or sort")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "The authenticated role is not allowed")
    BranchPageResponse findAll(
            @Parameter(description = "ACTIVE or INACTIVE")
            @RequestParam(required = false) String status,
            @Parameter(description = "Case-insensitive search over code, name and city")
            @RequestParam(required = false) String search,
            @Parameter(description = "Exact normalized city filter")
            @RequestParam(required = false) String city,
            @Parameter(description = "Two-letter ISO 3166 country filter")
            @RequestParam(required = false) String countryCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @Parameter(description = "CODE, NAME, CITY, CREATED_AT or UPDATED_AT")
            @RequestParam(defaultValue = "CODE") String sort,
            @Parameter(description = "ASC or DESC")
            @RequestParam(defaultValue = "ASC") String direction) {
        GymBranchSearchQuery query = new GymBranchSearchQuery(
                parseStatus(status),
                search,
                city,
                countryCode,
                page,
                size,
                parseSort(sort),
                parseDirection(direction));
        GymBranchPage result = service.findAll(query);
        return BranchPageResponse.from(result);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get a gym branch",
            description = "Returns one canonical branch. ADMIN and RECEPTIONIST may read; "
                    + "the branch identifier is not a tenant or assignment selector.")
    @ApiResponse(responseCode = "200", description = "Branch returned",
            content = @Content(schema = @Schema(implementation = BranchResponse.class)))
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "The authenticated role is not allowed")
    @ApiResponse(responseCode = "404", description = "Branch not found")
    BranchResponse findById(@PathVariable UUID id) {
        return BranchResponse.from(service.findById(id));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update a gym branch",
            description = "ADMIN only. Updates allowlisted detail and contact fields with an "
                    + "expected optimistic-lock version. Code, organization, status, initial marker, "
                    + "actor and timestamps are server controlled. Requires CSRF.")
    @ApiResponse(responseCode = "200", description = "Branch updated",
            content = @Content(schema = @Schema(implementation = BranchResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid branch request")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "ADMIN role or valid CSRF token required")
    @ApiResponse(responseCode = "404", description = "Branch not found")
    @ApiResponse(responseCode = "409", description = "Version conflict")
    BranchResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBranchRequest request,
            Authentication authentication) {
        return BranchResponse.from(service.update(id, request.toCommand(), actor(authentication)));
    }

    @PostMapping("/{id}/activate")
    @Operation(
            summary = "Activate a gym branch",
            description = "ADMIN only. Requires a reason, expected version and CSRF. The target "
                    + "status is server selected; the initial branch invariant remains enforced.")
    @ApiResponse(responseCode = "200", description = "Branch activated")
    @ApiResponse(responseCode = "400", description = "Invalid lifecycle request")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "ADMIN role or valid CSRF token required")
    @ApiResponse(responseCode = "404", description = "Branch not found")
    @ApiResponse(responseCode = "409", description = "Version or lifecycle conflict")
    BranchResponse activate(
            @PathVariable UUID id,
            @Valid @RequestBody BranchStatusChangeRequest request,
            Authentication authentication) {
        return BranchResponse.from(service.changeStatus(
                id, request.toCommand(GymBranchStatus.ACTIVE), actor(authentication)));
    }

    @PostMapping("/{id}/deactivate")
    @Operation(
            summary = "Deactivate a gym branch",
            description = "ADMIN only. Requires a reason, expected version and CSRF. The only "
                    + "active initial branch cannot be deactivated.")
    @ApiResponse(responseCode = "200", description = "Branch deactivated")
    @ApiResponse(responseCode = "400", description = "Invalid lifecycle request")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "ADMIN role or valid CSRF token required")
    @ApiResponse(responseCode = "404", description = "Branch not found")
    @ApiResponse(responseCode = "409", description = "Version or lifecycle conflict")
    BranchResponse deactivate(
            @PathVariable UUID id,
            @Valid @RequestBody BranchStatusChangeRequest request,
            Authentication authentication) {
        return BranchResponse.from(service.changeStatus(
                id, request.toCommand(GymBranchStatus.INACTIVE), actor(authentication)));
    }

    private static AuthenticatedActor actor(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof AuthenticatedActorProvider provider)) {
            throw new IllegalStateException("Authenticated staff principal is required.");
        }
        return provider.authenticatedActor();
    }

    private static GymBranchStatus parseStatus(String value) {
        return parseEnum(value, GymBranchStatus.class, "status");
    }

    private static GymBranchSortField parseSort(String value) {
        return parseEnum(value, GymBranchSortField.class, "sort");
    }

    private static GymBranchSortDirection parseDirection(String value) {
        return parseEnum(value, GymBranchSortDirection.class, "direction");
    }

    private static <T extends Enum<T>> T parseEnum(
            String value,
            Class<T> type,
            String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported branch " + field + " value.");
        }
    }
}
