package io.github.guillermodubon.coachgym.promotion.web;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.promotion.PromotionDetails;
import io.github.guillermodubon.coachgym.promotion.application.*;
import io.github.guillermodubon.coachgym.promotion.domain.PromotionValidationException;
import io.github.guillermodubon.coachgym.shared.web.ApiProblemFactory;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import io.github.guillermodubon.coachgym.promotion.DiscountType;
import io.swagger.v3.oas.annotations.Parameter;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/promotions")
@Tag(
        name = "Promotions",
        description = "Promotion catalog management and queries.")
class PromotionController {

    private final PromotionApplicationService promotionApplicationService;
    private final PromotionEligibilityApplicationService promotionEligibilityApplicationService;


    PromotionController(
            PromotionApplicationService
                    promotionApplicationService,
            PromotionEligibilityApplicationService
                    promotionEligibilityApplicationService) {

        this.promotionApplicationService =
                promotionApplicationService;

        this.promotionEligibilityApplicationService =
                promotionEligibilityApplicationService;
    }

    @PostMapping
    @Operation(
            summary = "Create a promotion",
            description = """
                    Creates an active percentage or fixed-amount promotion.
                    Only administrators can execute this operation.
                    Eligible membership plans are managed separately.
                    """)
    @ApiResponse(
            responseCode = "201",
            description = "Promotion created")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid promotion definition")
    @ApiResponse(
            responseCode = "401",
            description = "Authentication required")
    @ApiResponse(
            responseCode = "403",
            description = "Only administrators can create promotions")
    ResponseEntity<PromotionResponse> create(
            @Valid @RequestBody CreatePromotionRequest request,
            Authentication authentication,
            UriComponentsBuilder uriBuilder) {

        PromotionDetails promotion =
                promotionApplicationService.create(
                        request.toCommand(),
                        actor(authentication));

        URI location = uriBuilder
                .path("/api/v1/promotions/{id}")
                .buildAndExpand(promotion.id())
                .toUri();

        return ResponseEntity
                .created(location)
                .body(PromotionResponse.from(promotion));
    }

    @GetMapping
    @Operation(
            summary = "List promotions",
            description = """
                Returns a paginated promotion catalog.

                Filters are optional. The validOn filter includes promotions
                whose validity interval contains the provided date.

                Sorting uses separate sort and direction parameters.
                Supported sort fields are name, valid_from, valid_until,
                created_at and updated_at.
                """)
    @ApiResponse(
            responseCode = "200",
            description = "Promotions returned")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid pagination or sorting value")
    @ApiResponse(
            responseCode = "401",
            description = "Authentication required")
    @ApiResponse(
            responseCode = "403",
            description = "Insufficient permissions")
    PromotionPageResponse findAll(
            @Parameter(
                    description = "Filter by active state")
            @RequestParam(required = false)
            Boolean active,

            @Parameter(
                    description =
                            "Case-insensitive partial promotion-name filter")
            @RequestParam(required = false)
            String name,

            @Parameter(
                    description =
                            "Filter by PERCENTAGE or FIXED_AMOUNT")
            @RequestParam(required = false)
            DiscountType discountType,

            @Parameter(
                    description =
                            "Filter promotions valid on this inclusive date",
                    example = "2026-09-15")
            @RequestParam(required = false)
            LocalDate validOn,

            @Parameter(
                    description = "Zero-based page index",
                    example = "0")
            @RequestParam(defaultValue = "0")
            int page,

            @Parameter(
                    description =
                            "Page size between 1 and 100",
                    example = "25")
            @RequestParam(defaultValue = "25")
            int size,

            @Parameter(
                    description = """
                        Sort field: name, valid_from, valid_until,
                        created_at or updated_at
                        """,
                    example = "name")
            @RequestParam(defaultValue = "name")
            String sort,

            @Parameter(
                    description =
                            "Sort direction: asc or desc",
                    example = "asc")
            @RequestParam(defaultValue = "asc")
            String direction) {

        PromotionSearchQuery query =
                PromotionSearchQuery.from(
                        active,
                        name,
                        discountType,
                        validOn,
                        page,
                        size,
                        sort,
                        direction);

        return PromotionPageResponse.from(
                promotionApplicationService.findAll(query));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update a promotion",
            description = """
                Updates the commercial definition of a promotion.
                The request must contain the current optimistic-lock version.
                Only administrators can execute this operation.
                """)
    @ApiResponse(
            responseCode = "200",
            description = "Promotion updated")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid promotion definition")
    @ApiResponse(
            responseCode = "401",
            description = "Authentication required")
    @ApiResponse(
            responseCode = "403",
            description = "Only administrators can update promotions")
    @ApiResponse(
            responseCode = "404",
            description = "Promotion not found")
    @ApiResponse(
            responseCode = "409",
            description = "Promotion version conflict")
    PromotionResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody
            UpdatePromotionRequest request,
            Authentication authentication) {

        return PromotionResponse.from(
                promotionApplicationService.update(
                        id,
                        request.toCommand(),
                        actor(authentication)));
    }

    @PostMapping("/{id}/deactivate")
    @Operation(
            summary = "Deactivate a promotion",
            description = """
                Explicitly deactivates a promotion.
                A deactivated promotion cannot be applied to future memberships.
                """)
    @ApiResponse(
            responseCode = "200",
            description = "Promotion deactivated")
    @ApiResponse(
            responseCode = "401",
            description = "Authentication required")
    @ApiResponse(
            responseCode = "403",
            description = "Only administrators can deactivate promotions")
    @ApiResponse(
            responseCode = "404",
            description = "Promotion not found")
    @ApiResponse(
            responseCode = "409",
            description = "Version or state conflict")
    PromotionResponse deactivate(
            @PathVariable UUID id,
            @Valid @RequestBody
            PromotionStateRequest request,
            Authentication authentication) {

        return PromotionResponse.from(
                promotionApplicationService.deactivate(
                        id,
                        request.version(),
                        actor(authentication)));
    }

    @PostMapping("/{id}/activate")
    @Operation(
            summary = "Activate a promotion",
            description = """
                Explicitly reactivates an inactive promotion.
                Activation does not bypass validity-date or plan-eligibility rules.
                """)
    @ApiResponse(
            responseCode = "200",
            description = "Promotion activated")
    @ApiResponse(
            responseCode = "401",
            description = "Authentication required")
    @ApiResponse(
            responseCode = "403",
            description = "Only administrators can activate promotions")
    @ApiResponse(
            responseCode = "404",
            description = "Promotion not found")
    @ApiResponse(
            responseCode = "409",
            description = "Version or state conflict")
    PromotionResponse activate(
            @PathVariable UUID id,
            @Valid @RequestBody
            PromotionStateRequest request,
            Authentication authentication) {

        return PromotionResponse.from(
                promotionApplicationService.activate(
                        id,
                        request.version(),
                        actor(authentication)));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get a promotion",
            description = """
                    Returns a promotion by its internal identifier.
                    Administrators and receptionists can query promotions.
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "Promotion found")
    @ApiResponse(
            responseCode = "401",
            description = "Authentication required")
    @ApiResponse(
            responseCode = "403",
            description = "Insufficient permissions")
    @ApiResponse(
            responseCode = "404",
            description = "Promotion not found")
    PromotionResponse findById(@PathVariable UUID id) {
        return PromotionResponse.from(
                promotionApplicationService.findById(id));
    }

    @GetMapping("/{id}/eligible-plans")
    @Operation(
            summary = "Get the eligible plans of a promotion",
            description = """
                Returns the membership plans explicitly associated
                with a promotion.

                An empty items collection means that the promotion is
                not currently eligible for any membership plan.

                Administrators and receptionists can query this
                configuration.
                """)
    @ApiResponse(
            responseCode = "200",
            description = "Eligible plans returned")
    @ApiResponse(
            responseCode = "401",
            description = "Authentication required")
    @ApiResponse(
            responseCode = "403",
            description = "Insufficient permissions")
    @ApiResponse(
            responseCode = "404",
            description = "Promotion not found")
    PromotionEligiblePlansResponse findEligiblePlans(
            @PathVariable UUID id) {

        return PromotionEligiblePlansResponse.from(
                promotionEligibilityApplicationService
                        .findEligiblePlans(id));
    }

    @PutMapping("/{id}/eligible-plans")
    @Operation(
            summary = "Replace the eligible plans of a promotion",
            description = """
                Atomically replaces the complete set of membership
                plans eligible for a promotion.

                All supplied membership plans must exist and be active.
                An empty planIds set removes all plan eligibility.

                The request must contain the current optimistic-lock
                version of the promotion.

                Only administrators can execute this operation.
                """)
    @ApiResponse(
            responseCode = "200",
            description = "Eligible plans replaced")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid eligible-plan request")
    @ApiResponse(
            responseCode = "401",
            description = "Authentication required")
    @ApiResponse(
            responseCode = "403",
            description =
                    "Only administrators can manage eligible plans")
    @ApiResponse(
            responseCode = "404",
            description =
                    "Promotion or membership plan not found")
    @ApiResponse(
            responseCode = "409",
            description =
                    "Promotion version conflict or inactive plan")
    PromotionEligiblePlansResponse replaceEligiblePlans(
            @PathVariable UUID id,
            @Valid
            @RequestBody
            ReplaceEligiblePlansRequest request,
            Authentication authentication) {

        return PromotionEligiblePlansResponse.from(
                promotionEligibilityApplicationService
                        .replaceEligiblePlans(
                                id,
                                request.toCommand(),
                                actor(authentication)));
    }

    private static AuthenticatedActor actor(
            Authentication authentication) {

        CoachGymUserPrincipal principal =
                (CoachGymUserPrincipal) authentication.getPrincipal();

        return principal.authenticatedActor();
    }

    @ExceptionHandler(PromotionValidationException.class)
    ResponseEntity<ProblemDetail> handleValidation(
            PromotionValidationException exception) {

        return ResponseEntity
                .badRequest()
                .body(ApiProblemFactory.create(
                        HttpStatus.BAD_REQUEST,
                        "PROMOTION_VALIDATION_FAILED",
                        exception.getMessage()));
    }

    @ExceptionHandler(PromotionNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(
            PromotionNotFoundException exception) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiProblemFactory.create(
                        HttpStatus.NOT_FOUND,
                        "PROMOTION_NOT_FOUND",
                        exception.getMessage()));
    }

    @ExceptionHandler(
            EligiblePlanNotFoundException.class)
    ResponseEntity<ProblemDetail> handleEligiblePlanNotFound(
            EligiblePlanNotFoundException exception) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(
                        ApiProblemFactory.create(
                                HttpStatus.NOT_FOUND,
                                "ELIGIBLE_PLAN_NOT_FOUND",
                                exception.getMessage()));
    }

    @ExceptionHandler(
            InactiveEligiblePlanException.class)
    ResponseEntity<ProblemDetail> handleInactiveEligiblePlan(
            InactiveEligiblePlanException exception) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(
                        ApiProblemFactory.create(
                                HttpStatus.CONFLICT,
                                "ELIGIBLE_PLAN_INACTIVE",
                                exception.getMessage()));
    }

    @ExceptionHandler(
            PromotionVersionConflictException.class)
    ResponseEntity<ProblemDetail> handleVersionConflict(
            PromotionVersionConflictException exception) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(
                        ApiProblemFactory.create(
                                HttpStatus.CONFLICT,
                                "PROMOTION_VERSION_CONFLICT",
                                exception.getMessage()));
    }

    @ExceptionHandler(
            PromotionStateConflictException.class)
    ResponseEntity<ProblemDetail> handleStateConflict(
            PromotionStateConflictException exception) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(
                        ApiProblemFactory.create(
                                HttpStatus.CONFLICT,
                                "PROMOTION_STATE_CONFLICT",
                                exception.getMessage()));
    }
}
