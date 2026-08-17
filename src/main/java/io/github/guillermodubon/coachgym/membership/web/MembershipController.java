package io.github.guillermodubon.coachgym.membership.web;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.membership.MembershipDetails;
import io.github.guillermodubon.coachgym.membership.application.CurrentMembershipAlreadyExistsException;
import io.github.guillermodubon.coachgym.membership.application.InactiveMembershipClientException;
import io.github.guillermodubon.coachgym.membership.application.MembershipApplicationService;
import io.github.guillermodubon.coachgym.membership.application.MembershipClientNotFoundException;
import io.github.guillermodubon.coachgym.membership.application.MembershipNotFoundException;
import io.github.guillermodubon.coachgym.membership.application.MembershipPlanNotAvailableException;
import io.github.guillermodubon.coachgym.membership.domain.MembershipValidationException;
import io.github.guillermodubon.coachgym.promotion.PromotionEvaluationException;
import io.github.guillermodubon.coachgym.promotion.PromotionEvaluationFailure;
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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/memberships")
@Tag(
        name = "Memberships",
        description =
                "Membership creation and administrative queries.")
class MembershipController {

    private final MembershipApplicationService
            membershipApplicationService;

    MembershipController(
            MembershipApplicationService
                    membershipApplicationService) {

        this.membershipApplicationService =
                membershipApplicationService;
    }

    @PostMapping
    @Operation(
            summary = "Create an initial membership",
            description = """
                    Creates an active membership and its first
                    membership period.

                    The client must exist and be active. The membership
                    plan must exist and be active. A client cannot have
                    another active or frozen membership.

                    If a promotion is supplied, it must be active,
                    valid on the membership start date and explicitly
                    eligible for the selected plan.

                    Administrators and receptionists can execute this
                    operation.
                    """)
    @ApiResponse(
            responseCode = "201",
            description = "Membership created")
    @ApiResponse(
            responseCode = "400",
            description =
                    "Invalid membership creation request")
    @ApiResponse(
            responseCode = "401",
            description = "Authentication required")
    @ApiResponse(
            responseCode = "403",
            description = "Insufficient permissions")
    @ApiResponse(
            responseCode = "404",
            description =
                    "Client, plan or promotion not found")
    @ApiResponse(
            responseCode = "409",
            description = """
                    Client is inactive, current membership already
                    exists, plan is unavailable or promotion cannot
                    be applied
                    """)
    ResponseEntity<MembershipResponse> create(
            @Valid
            @RequestBody
            CreateMembershipRequest request,
            Authentication authentication,
            UriComponentsBuilder uriBuilder) {

        MembershipDetails membership =
                membershipApplicationService.create(
                        request.toCommand(),
                        actor(authentication));

        URI location =
                uriBuilder
                        .path(
                                "/api/v1/memberships/{id}")
                        .buildAndExpand(
                                membership.id())
                        .toUri();

        return ResponseEntity
                .created(location)
                .body(
                        MembershipResponse.from(
                                membership));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get a membership",
            description = """
                    Returns a membership and its current commercial
                    period, including historical plan and promotion
                    snapshots.

                    Administrators and receptionists can execute this
                    operation.
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "Membership returned")
    @ApiResponse(
            responseCode = "401",
            description = "Authentication required")
    @ApiResponse(
            responseCode = "403",
            description = "Insufficient permissions")
    @ApiResponse(
            responseCode = "404",
            description = "Membership not found")
    MembershipResponse findById(
            @PathVariable UUID id) {

        return MembershipResponse.from(
                membershipApplicationService
                        .findById(id));
    }

    private static AuthenticatedActor actor(
            Authentication authentication) {

        CoachGymUserPrincipal principal =
                (CoachGymUserPrincipal)
                        authentication.getPrincipal();

        return principal.authenticatedActor();
    }

    @ExceptionHandler(
            MembershipValidationException.class)
    ResponseEntity<ProblemDetail> handleValidation(
            MembershipValidationException exception) {

        return problem(
                HttpStatus.BAD_REQUEST,
                "MEMBERSHIP_VALIDATION_FAILED",
                exception.getMessage());
    }

    @ExceptionHandler(
            MembershipNotFoundException.class)
    ResponseEntity<ProblemDetail> handleMembershipNotFound(
            MembershipNotFoundException exception) {

        return problem(
                HttpStatus.NOT_FOUND,
                "MEMBERSHIP_NOT_FOUND",
                exception.getMessage());
    }

    @ExceptionHandler(
            MembershipClientNotFoundException.class)
    ResponseEntity<ProblemDetail> handleClientNotFound(
            MembershipClientNotFoundException exception) {

        return problem(
                HttpStatus.NOT_FOUND,
                "MEMBERSHIP_CLIENT_NOT_FOUND",
                exception.getMessage());
    }

    @ExceptionHandler(
            MembershipPlanNotAvailableException.class)
    ResponseEntity<ProblemDetail> handlePlanNotAvailable(
            MembershipPlanNotAvailableException exception) {

        return problem(
                HttpStatus.CONFLICT,
                "MEMBERSHIP_PLAN_NOT_AVAILABLE",
                exception.getMessage());
    }

    @ExceptionHandler(
            InactiveMembershipClientException.class)
    ResponseEntity<ProblemDetail> handleInactiveClient(
            InactiveMembershipClientException exception) {

        return problem(
                HttpStatus.CONFLICT,
                "MEMBERSHIP_CLIENT_INACTIVE",
                exception.getMessage());
    }

    @ExceptionHandler(
            CurrentMembershipAlreadyExistsException.class)
    ResponseEntity<ProblemDetail> handleCurrentMembershipConflict(
            CurrentMembershipAlreadyExistsException exception) {

        return problem(
                HttpStatus.CONFLICT,
                "CURRENT_MEMBERSHIP_ALREADY_EXISTS",
                exception.getMessage());
    }

    @ExceptionHandler(
            PromotionEvaluationException.class)
    ResponseEntity<ProblemDetail> handlePromotionEvaluation(
            PromotionEvaluationException exception) {

        HttpStatus status =
                promotionEvaluationStatus(
                        exception.failure());

        return problem(
                status,
                promotionEvaluationCode(
                        exception.failure()),
                exception.getMessage());
    }

    private static HttpStatus promotionEvaluationStatus(
            PromotionEvaluationFailure failure) {

        return switch (failure) {
            case PROMOTION_NOT_FOUND ->
                    HttpStatus.NOT_FOUND;

            case PROMOTION_INACTIVE,
                 PROMOTION_NOT_YET_VALID,
                 PROMOTION_EXPIRED,
                 PLAN_NOT_ELIGIBLE,
                 CURRENCY_MISMATCH ->
                    HttpStatus.CONFLICT;

            case INVALID_PRICE,
                 INVALID_CURRENCY ->
                    HttpStatus.BAD_REQUEST;
        };
    }

    private static String promotionEvaluationCode(
            PromotionEvaluationFailure failure) {

        return switch (failure) {
            case PROMOTION_NOT_FOUND ->
                    "MEMBERSHIP_PROMOTION_NOT_FOUND";

            case PROMOTION_INACTIVE ->
                    "MEMBERSHIP_PROMOTION_INACTIVE";

            case PROMOTION_NOT_YET_VALID ->
                    "MEMBERSHIP_PROMOTION_NOT_YET_VALID";

            case PROMOTION_EXPIRED ->
                    "MEMBERSHIP_PROMOTION_EXPIRED";

            case PLAN_NOT_ELIGIBLE ->
                    "MEMBERSHIP_PROMOTION_PLAN_NOT_ELIGIBLE";

            case CURRENCY_MISMATCH ->
                    "MEMBERSHIP_PROMOTION_CURRENCY_MISMATCH";

            case INVALID_PRICE ->
                    "MEMBERSHIP_PROMOTION_INVALID_PRICE";

            case INVALID_CURRENCY ->
                    "MEMBERSHIP_PROMOTION_INVALID_CURRENCY";
        };
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String code,
            String detail) {

        return ResponseEntity
                .status(status)
                .body(
                        ApiProblemFactory.create(
                                status,
                                code,
                                detail));
    }
}
