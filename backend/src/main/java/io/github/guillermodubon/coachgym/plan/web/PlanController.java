package io.github.guillermodubon.coachgym.plan.web;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.plan.PlanDetails;
import io.github.guillermodubon.coachgym.plan.MembershipPlanBranchCoverageValidationException;
import io.github.guillermodubon.coachgym.plan.application.PlanApplicationService;
import io.github.guillermodubon.coachgym.plan.application.PlanCoverageApplicationService;
import io.github.guillermodubon.coachgym.plan.application.PlanNotFoundException;
import io.github.guillermodubon.coachgym.plan.application.PlanSearchQuery;
import io.github.guillermodubon.coachgym.plan.application.PlanStateConflictException;
import io.github.guillermodubon.coachgym.plan.application.PlanVersionConflictException;
import io.github.guillermodubon.coachgym.plan.domain.PlanValidationException;
import io.github.guillermodubon.coachgym.shared.web.ApiProblemFactory;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.StaffBranchAuthorizationException;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/plans")
@Tag(name = "Plans", description = "Membership plan catalog management.")
@SecurityRequirement(name = "sessionCookie")
class PlanController {

    private final PlanApplicationService planApplicationService;
    private final PlanCoverageApplicationService planCoverageApplicationService;

    PlanController(
            PlanApplicationService planApplicationService,
            PlanCoverageApplicationService planCoverageApplicationService) {
        this.planApplicationService = planApplicationService;
        this.planCoverageApplicationService = planCoverageApplicationService;
    }

    @PostMapping
    @Operation(summary = "Create a membership plan")
    @ApiResponse(responseCode = "201", description = "Plan created")
    @ApiResponse(responseCode = "403", description = "Only administrators can manage plans")
    ResponseEntity<PlanResponse> create(
            @Valid @RequestBody CreatePlanRequest request,
            Authentication authentication,
            UriComponentsBuilder uriBuilder) {
        PlanDetails plan = planApplicationService.create(request.toCommand(), actor(authentication));
        URI location = uriBuilder.path("/api/v1/plans/{id}").buildAndExpand(plan.id()).toUri();
        return ResponseEntity.created(location).body(PlanResponse.from(plan));
    }

    @GetMapping("/{id}/branch-coverage")
    @Operation(
            summary = "Read a plan's branch coverage",
            description = "Returns the mutable plan definition, including its scope, explicit branch set, and optimistic-lock version. Only an active organization-scoped ADMIN may read the cross-branch set. Membership-period entitlements are separate immutable snapshots.")
    @ApiResponse(responseCode = "200", description = "Coverage returned")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Organization ADMIN scope required")
    @ApiResponse(responseCode = "404", description = "Plan not found")
    PlanBranchCoverageResponse findCoverage(
            @PathVariable UUID id,
            Authentication authentication) {
        return PlanBranchCoverageResponse.from(
                planCoverageApplicationService.findCoverageForAdministration(
                        id, actor(authentication)));
    }

    @PutMapping("/{id}/branch-coverage")
    @Operation(
            summary = "Replace a plan's branch coverage",
            description = "Atomically replaces the complete coverage definition. SINGLE_BRANCH requires one active canonical branch, SELECTED_BRANCHES requires at least two, and ALL_BRANCHES requires an empty branchIds list. The request version is optimistic locking, branch IDs never grant authority, only an active organization ADMIN may update, and CSRF is required. Existing membership-period snapshots are not changed.")
    @ApiResponse(responseCode = "200", description = "Coverage updated or unchanged")
    @ApiResponse(responseCode = "400", description = "Invalid scope, branch set, or request version")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Organization ADMIN scope or valid CSRF token required")
    @ApiResponse(responseCode = "404", description = "Plan not found")
    @ApiResponse(responseCode = "409", description = "Plan version conflict")
    PlanBranchCoverageResponse replaceCoverage(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePlanBranchCoverageRequest request,
            Authentication authentication) {
        return PlanBranchCoverageResponse.from(
                planCoverageApplicationService.replaceCoverage(
                        id,
                        request.toCommand(),
                        actor(authentication)));
    }

    @GetMapping
    @Operation(
            summary = "List membership plans",
            description = """
                Returns a paginated catalog limited to plans valid at the
                authenticated staff member's active branch. An organization
                ADMIN may provide another active branch ID only when the server
                confirms it is authorized. Sorting uses separate sort and
                direction parameters. Supported sort values are name, created_at
                and updated_at. Supported directions are asc and desc.
                """)
    @ApiResponse(responseCode = "200", description = "Plans returned")
    @ApiResponse(responseCode = "400", description = "Invalid pagination, filter or sorting value")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    PlanPageResponse findAll(
            @Parameter(description = "Filter by active state")
            @RequestParam(required = false)
            Boolean active,

            @Parameter(description = "Case-insensitive partial plan-name filter")
            @RequestParam(required = false)
            String name,

            @Parameter(description = "Optional active branch ID. It is only a requested filter; persisted staff scope and assignments determine authority.")
            @RequestParam(required = false)
            UUID branchId,

            Authentication authentication,

            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0")
            int page,

            @Parameter(description = "Page size between 1 and 100", example = "25")
            @RequestParam(defaultValue = "25")
            int size,

            @Parameter(
                    description = "Sort field: name, created_at or updated_at",
                    example = "name")
            @RequestParam(defaultValue = "name")
            String sort,

            @Parameter(
                    description = "Sort direction: asc or desc",
                    example = "asc")
            @RequestParam(defaultValue = "asc")
            String direction) {

        PlanSearchQuery query =
                PlanSearchQuery.from(active, name, page, size, sort, direction);

        return PlanPageResponse.from(planCoverageApplicationService.findVisiblePlans(
                query,
                branchId,
                actor(authentication)));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get a membership plan",
            description = "Returns a plan only when it is valid at the authenticated staff member's server-resolved active branch. A request branch identifier never grants detail access.")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions or staff scope")
    @ApiResponse(responseCode = "404", description = "Plan not found")
    PlanResponse findById(@PathVariable UUID id, Authentication authentication) {
        return PlanResponse.from(planCoverageApplicationService.findVisiblePlan(
                id, actor(authentication)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a membership plan")
    @ApiResponse(responseCode = "409", description = "Plan version conflict")
    PlanResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePlanRequest request,
            Authentication authentication) {
        return PlanResponse.from(planApplicationService.update(id, request.toCommand(), actor(authentication)));
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a membership plan")
    PlanResponse deactivate(
            @PathVariable UUID id,
            @Valid @RequestBody PlanStateRequest request,
            Authentication authentication) {
        return PlanResponse.from(planApplicationService.deactivate(
                id,
                request.version(),
                actor(authentication)));
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate a membership plan")
    PlanResponse activate(
            @PathVariable UUID id,
            @Valid @RequestBody PlanStateRequest request,
            Authentication authentication) {
        return PlanResponse.from(planApplicationService.activate(
                id,
                request.version(),
                actor(authentication)));
    }

    private static AuthenticatedActor actor(Authentication authentication) {
        CoachGymUserPrincipal principal = (CoachGymUserPrincipal) authentication.getPrincipal();
        return principal.authenticatedActor();
    }

    @ExceptionHandler(PlanValidationException.class)
    ResponseEntity<ProblemDetail> handleValidation(PlanValidationException exception) {
        return ResponseEntity.badRequest().body(ApiProblemFactory.create(
                HttpStatus.BAD_REQUEST, "PLAN_VALIDATION_FAILED", exception.getMessage()));
    }

    @ExceptionHandler(PlanNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(PlanNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiProblemFactory.create(
                HttpStatus.NOT_FOUND,
                "PLAN_NOT_FOUND",
                "The requested plan was not found."));
    }

    @ExceptionHandler(MembershipPlanBranchCoverageValidationException.class)
    ResponseEntity<ProblemDetail> handleCoverageValidation(
            MembershipPlanBranchCoverageValidationException exception) {
        return ResponseEntity.badRequest().body(ApiProblemFactory.create(
                HttpStatus.BAD_REQUEST,
                "PLAN_BRANCH_COVERAGE_VALIDATION_FAILED",
                exception.getMessage()));
    }

    @ExceptionHandler(StaffBranchAuthorizationException.class)
    ResponseEntity<ProblemDetail> handleStaffScopeAuthorization(
            StaffBranchAuthorizationException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiProblemFactory.create(
                HttpStatus.FORBIDDEN,
                "PLAN_OPERATION_FORBIDDEN",
                "The authenticated staff scope cannot perform this plan operation."));
    }

    @ExceptionHandler({PlanVersionConflictException.class, PlanStateConflictException.class})
    ResponseEntity<ProblemDetail> handleConflict(RuntimeException exception) {
        String code = exception instanceof PlanVersionConflictException
                ? "PLAN_VERSION_CONFLICT"
                : "PLAN_STATE_CONFLICT";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiProblemFactory.create(
                HttpStatus.CONFLICT, code, exception.getMessage()));
    }
}
