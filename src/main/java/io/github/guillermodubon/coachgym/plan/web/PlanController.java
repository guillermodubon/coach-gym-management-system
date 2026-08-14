package io.github.guillermodubon.coachgym.plan.web;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.plan.PlanDetails;
import io.github.guillermodubon.coachgym.plan.application.PlanApplicationService;
import io.github.guillermodubon.coachgym.plan.application.PlanNotFoundException;
import io.github.guillermodubon.coachgym.plan.application.PlanSearchQuery;
import io.github.guillermodubon.coachgym.plan.application.PlanStateConflictException;
import io.github.guillermodubon.coachgym.plan.application.PlanVersionConflictException;
import io.github.guillermodubon.coachgym.plan.domain.PlanValidationException;
import io.github.guillermodubon.coachgym.shared.web.ApiProblemFactory;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
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
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/plans")
@Tag(name = "Plans", description = "Membership plan catalog management.")
class PlanController {

    private final PlanApplicationService planApplicationService;

    PlanController(PlanApplicationService planApplicationService) {
        this.planApplicationService = planApplicationService;
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

    @GetMapping
    @Operation(
            summary = "List membership plans",
            description = """
                Returns a paginated plan catalog. Sorting uses separate sort and
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

        return PlanPageResponse.from(planApplicationService.findAll(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a membership plan")
    @ApiResponse(responseCode = "404", description = "Plan not found")
    PlanResponse findById(@PathVariable UUID id) {
        return PlanResponse.from(planApplicationService.findById(id));
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
                HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", exception.getMessage()));
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
