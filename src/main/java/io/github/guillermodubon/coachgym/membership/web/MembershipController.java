package io.github.guillermodubon.coachgym.membership.web;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.membership.MembershipDetails;
import io.github.guillermodubon.coachgym.membership.application.*;
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

    private final MembershipFreezeApplicationService
            membershipFreezeApplicationService;

    MembershipController(
            MembershipApplicationService membershipApplicationService,
            MembershipFreezeApplicationService
                    membershipFreezeApplicationService) {

        this.membershipApplicationService =
                membershipApplicationService;

        this.membershipFreezeApplicationService =
                membershipFreezeApplicationService;
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

    @PostMapping("/{id}/renew")
    @Operation(
            summary = "Renew a membership",
            description = """
                    Creates a new commercial period for an existing
                    membership.
                    
                    An active membership is renewed from the current
                    period's effective end date. A requested start date is
                    ignored for an active membership.
                    
                    An expired membership requires a start date that is
                    not before the current operational date. A successful
                    expired-membership renewal changes its status back to
                    ACTIVE.
                    
                    Frozen and cancelled memberships cannot be renewed.
                    
                    The selected plan must be active. An optional promotion
                    must be active, valid on the effective renewal start
                    date and explicitly eligible for the selected plan.
                    
                    The request must contain the current optimistic-lock
                    version of the membership.
                    
                    Administrators and receptionists can execute this
                    operation.
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "Membership renewed")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid membership renewal request")
    @ApiResponse(
            responseCode = "401",
            description = "Authentication required")
    @ApiResponse(
            responseCode = "403",
            description = "Insufficient permissions")
    @ApiResponse(
            responseCode = "404",
            description =
                    "Membership, client or promotion not found")
    @ApiResponse(
            responseCode = "409",
            description = """
                    Membership version conflict, non-renewable state,
                    inactive client, unavailable plan or promotion
                    evaluation conflict
                    """)
    MembershipResponse renew(
            @PathVariable UUID id,
            @Valid
            @RequestBody
            RenewMembershipRequest request,
            Authentication authentication) {

        return MembershipResponse.from(
                membershipApplicationService.renew(
                        id,
                        request.toCommand(),
                        actor(authentication)));
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
            case PROMOTION_NOT_FOUND -> HttpStatus.NOT_FOUND;

            case PROMOTION_INACTIVE,
                 PROMOTION_NOT_YET_VALID,
                 PROMOTION_EXPIRED,
                 PLAN_NOT_ELIGIBLE,
                 CURRENCY_MISMATCH -> HttpStatus.CONFLICT;

            case INVALID_PRICE,
                 INVALID_CURRENCY -> HttpStatus.BAD_REQUEST;
        };
    }

    private static String promotionEvaluationCode(
            PromotionEvaluationFailure failure) {

        return switch (failure) {
            case PROMOTION_NOT_FOUND -> "MEMBERSHIP_PROMOTION_NOT_FOUND";

            case PROMOTION_INACTIVE -> "MEMBERSHIP_PROMOTION_INACTIVE";

            case PROMOTION_NOT_YET_VALID -> "MEMBERSHIP_PROMOTION_NOT_YET_VALID";

            case PROMOTION_EXPIRED -> "MEMBERSHIP_PROMOTION_EXPIRED";

            case PLAN_NOT_ELIGIBLE -> "MEMBERSHIP_PROMOTION_PLAN_NOT_ELIGIBLE";

            case CURRENCY_MISMATCH -> "MEMBERSHIP_PROMOTION_CURRENCY_MISMATCH";

            case INVALID_PRICE -> "MEMBERSHIP_PROMOTION_INVALID_PRICE";

            case INVALID_CURRENCY -> "MEMBERSHIP_PROMOTION_INVALID_CURRENCY";
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

    @ExceptionHandler(
            MembershipNotRenewableException.class)
    ResponseEntity<ProblemDetail> handleNotRenewable(
            MembershipNotRenewableException exception) {

        return problem(
                HttpStatus.CONFLICT,
                "MEMBERSHIP_NOT_RENEWABLE",
                exception.getMessage());
    }

    @ExceptionHandler(
            MembershipVersionConflictException.class)
    ResponseEntity<ProblemDetail> handleVersionConflict(
            MembershipVersionConflictException exception) {

        return problem(
                HttpStatus.CONFLICT,
                "MEMBERSHIP_VERSION_CONFLICT",
                exception.getMessage());
    }

    @PostMapping("/{id}/freeze")
    @Operation(
            summary = "Freeze a membership",
            description = """
                    Freezes an active membership without creating a new
                    commercial period. The request must contain the current
                    optimistic-lock version of the membership.
                    Administrators and receptionists can execute this operation.
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "Membership frozen")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid freeze request")
    @ApiResponse(
            responseCode = "401",
            description = "Authentication required")
    @ApiResponse(
            responseCode = "403",
            description = "Insufficient permissions or invalid CSRF token")
    @ApiResponse(
            responseCode = "404",
            description = "Membership not found")
    @ApiResponse(
            responseCode = "409",
            description = "Membership version or state conflict")
    MembershipResponse freeze(
            @PathVariable UUID id,
            @Valid
            @RequestBody
            FreezeMembershipRequest request,
            Authentication authentication) {

        return MembershipResponse.from(
                membershipFreezeApplicationService.freeze(
                        id,
                        request.toCommand(),
                        actor(authentication)));
    }

    @PostMapping("/{id}/reactivate")
    @Operation(
            summary = "Reactivate a frozen membership",
            description = """
                    Reactivates a frozen membership without creating a new
                    commercial period. The associated client must remain active.
                    The request must contain the current optimistic-lock version
                    of the membership. Administrators and receptionists can
                    execute this operation.
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "Membership reactivated")
    @ApiResponse(
            responseCode = "400",
            description = "Invalid reactivation request")
    @ApiResponse(
            responseCode = "401",
            description = "Authentication required")
    @ApiResponse(
            responseCode = "403",
            description = "Insufficient permissions or invalid CSRF token")
    @ApiResponse(
            responseCode = "404",
            description = "Membership or open freeze not found")
    @ApiResponse(
            responseCode = "409",
            description = "Membership version, state or client conflict")
    MembershipResponse reactivate(
            @PathVariable UUID id,
            @Valid
            @RequestBody
            ReactivateMembershipRequest request,
            Authentication authentication) {

        return MembershipResponse.from(
                membershipFreezeApplicationService.reactivate(
                        id,
                        request.toCommand(),
                        actor(authentication)));
    }

    @ExceptionHandler(
            MembershipAlreadyFrozenException.class)
    ResponseEntity<ProblemDetail>
    handleAlreadyFrozen(
            MembershipAlreadyFrozenException exception) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(
                        ApiProblemFactory.create(
                                HttpStatus.CONFLICT,
                                "MEMBERSHIP_ALREADY_FROZEN",
                                exception.getMessage()));
    }

    @ExceptionHandler(
            MembershipFreezeStateConflictException.class)
    ResponseEntity<ProblemDetail>
    handleFreezeStateConflict(
            MembershipFreezeStateConflictException exception) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(
                        ApiProblemFactory.create(
                                HttpStatus.CONFLICT,
                                "MEMBERSHIP_FREEZE_STATE_CONFLICT",
                                exception.getMessage()));
    }

    @ExceptionHandler(
            MembershipNotFrozenException.class)
    ResponseEntity<ProblemDetail>
    handleNotFrozen(
            MembershipNotFrozenException exception) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(
                        ApiProblemFactory.create(
                                HttpStatus.CONFLICT,
                                "MEMBERSHIP_NOT_FROZEN",
                                exception.getMessage()));
    }

    @ExceptionHandler(
            MembershipFreezeNotFoundException.class)
    ResponseEntity<ProblemDetail>
    handleFreezeNotFound(
            MembershipFreezeNotFoundException exception) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(
                        ApiProblemFactory.create(
                                HttpStatus.NOT_FOUND,
                                "MEMBERSHIP_FREEZE_NOT_FOUND",
                                exception.getMessage()));
    }
}
