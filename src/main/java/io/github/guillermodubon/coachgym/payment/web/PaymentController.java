package io.github.guillermodubon.coachgym.payment.web;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.payment.PaymentDetails;
import io.github.guillermodubon.coachgym.payment.application.DuplicatePaymentReferenceException;
import io.github.guillermodubon.coachgym.payment.application.PaymentSearchQuery;
import io.github.guillermodubon.coachgym.payment.application.PaymentApplicationService;
import io.github.guillermodubon.coachgym.payment.application.PaymentMembershipNotFoundException;
import io.github.guillermodubon.coachgym.payment.application.PaymentNotFoundException;
import io.github.guillermodubon.coachgym.payment.application.PaymentPeriodMismatchException;
import io.github.guillermodubon.coachgym.payment.application.PaymentPeriodNotFoundException;
import io.github.guillermodubon.coachgym.payment.domain.PaymentAmountMismatchException;
import io.github.guillermodubon.coachgym.payment.domain.PaymentCurrencyMismatchException;
import io.github.guillermodubon.coachgym.payment.domain.PaymentMembershipMismatchException;
import io.github.guillermodubon.coachgym.payment.domain.PaymentMembershipStateConflictException;
import io.github.guillermodubon.coachgym.payment.domain.PaymentValidationException;
import io.github.guillermodubon.coachgym.shared.web.ApiProblemFactory;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(
        name = "Payments",
        description = "Payment registration and retrieval.")
class PaymentController {

    private final PaymentApplicationService paymentApplicationService;

    PaymentController(PaymentApplicationService paymentApplicationService) {
        this.paymentApplicationService = paymentApplicationService;
    }

    @PostMapping
    @Operation(
            summary = "Register a payment",
            description = """
                    Registers a payment for a specific membership period.

                    The client, membership, and period must exist and belong
                    to each other. The amount must equal the period final price.
                    The currency must match the period pricing snapshot.
                    The membership must not be cancelled.
                    The paid-at timestamp must not be in the future.

                    If an external reference is supplied it must be unique
                    for the given payment method.

                    Administrators and receptionists can execute this operation.
                    """)
    @ApiResponse(responseCode = "201", description = "Payment registered")
    @ApiResponse(responseCode = "400", description = "Invalid request or financial rule violation")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions or invalid CSRF token")
    @ApiResponse(responseCode = "404", description = "Client, membership or period not found")
    @ApiResponse(responseCode = "409", description = "Relationship mismatch, state conflict or duplicate reference")
    ResponseEntity<PaymentResponse> register(
            @Valid @RequestBody RegisterPaymentRequest request,
            Authentication authentication,
            UriComponentsBuilder uriBuilder) {

        PaymentDetails payment =
                paymentApplicationService.register(
                        request.toCommand(),
                        actor(authentication));

        URI location =
                uriBuilder
                        .path("/api/v1/payments/{id}")
                        .buildAndExpand(payment.id())
                        .toUri();

        return ResponseEntity
                .created(location)
                .body(PaymentResponse.from(payment));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get a payment",
            description = """
                    Returns a payment by its identifier.

                    Administrators and receptionists can execute this operation.
                    """)
    @ApiResponse(responseCode = "200", description = "Payment returned")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @ApiResponse(responseCode = "404", description = "Payment not found")
    PaymentResponse findById(@PathVariable UUID id) {
        return PaymentResponse.from(
                paymentApplicationService.findById(id));
    }

    @GetMapping
    @Operation(
            summary = "List payments",
            description = """
                    Returns a paginated, filterable list of payments.

                    Approved filters: clientId, membershipId, membershipPeriodId,
                    status, paymentMethod, paidFrom, paidUntil.

                    Approved sort fields: PAID_AT (default), AMOUNT, CREATED_AT,
                    UPDATED_AT. Default direction: DESC.

                    Page is zero-based; size must be between 1 and 100.

                    Administrators and receptionists can execute this operation.
                    """)
    @ApiResponse(responseCode = "200", description = "Payment list returned")
    @ApiResponse(responseCode = "400", description = "Invalid filter or pagination parameters")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    PaymentPageResponse findAll(
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) UUID membershipId,
            @RequestParam(required = false) UUID membershipPeriodId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) Instant paidFrom,
            @RequestParam(required = false) Instant paidUntil,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "PAID_AT") String sort,
            @RequestParam(defaultValue = "DESC") String direction) {

        PaymentSearchQuery query = PaymentSearchQuery.from(
                clientId, membershipId, membershipPeriodId,
                status, paymentMethod, paidFrom, paidUntil,
                page, size, sort, direction);

        return PaymentPageResponse.from(
                paymentApplicationService.findAll(query));
    }

    // ------------------------------------------------------------------
    // Exception handlers
    // ------------------------------------------------------------------

    @ExceptionHandler(PaymentValidationException.class)
    ResponseEntity<ProblemDetail> handleValidation(
            PaymentValidationException ex) {

        return problem(HttpStatus.BAD_REQUEST,
                "PAYMENT_VALIDATION_FAILED", ex.getMessage());
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    ResponseEntity<ProblemDetail> handlePaymentNotFound(
            PaymentNotFoundException ex) {

        return problem(HttpStatus.NOT_FOUND,
                "PAYMENT_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(PaymentMembershipNotFoundException.class)
    ResponseEntity<ProblemDetail> handleMembershipNotFound(
            PaymentMembershipNotFoundException ex) {

        return problem(HttpStatus.NOT_FOUND,
                "PAYMENT_MEMBERSHIP_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(PaymentPeriodNotFoundException.class)
    ResponseEntity<ProblemDetail> handlePeriodNotFound(
            PaymentPeriodNotFoundException ex) {

        return problem(HttpStatus.NOT_FOUND,
                "PAYMENT_PERIOD_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(PaymentMembershipMismatchException.class)
    ResponseEntity<ProblemDetail> handleMembershipMismatch(
            PaymentMembershipMismatchException ex) {

        return problem(HttpStatus.CONFLICT,
                "PAYMENT_MEMBERSHIP_MISMATCH", ex.getMessage());
    }

    @ExceptionHandler(PaymentPeriodMismatchException.class)
    ResponseEntity<ProblemDetail> handlePeriodMismatch(
            PaymentPeriodMismatchException ex) {

        return problem(HttpStatus.CONFLICT,
                "PAYMENT_PERIOD_MISMATCH", ex.getMessage());
    }

    @ExceptionHandler(PaymentMembershipStateConflictException.class)
    ResponseEntity<ProblemDetail> handleMembershipStateConflict(
            PaymentMembershipStateConflictException ex) {

        return problem(HttpStatus.CONFLICT,
                "PAYMENT_MEMBERSHIP_STATE_CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(PaymentAmountMismatchException.class)
    ResponseEntity<ProblemDetail> handleAmountMismatch(
            PaymentAmountMismatchException ex) {

        return problem(HttpStatus.CONFLICT,
                "PAYMENT_AMOUNT_MISMATCH", ex.getMessage());
    }

    @ExceptionHandler(PaymentCurrencyMismatchException.class)
    ResponseEntity<ProblemDetail> handleCurrencyMismatch(
            PaymentCurrencyMismatchException ex) {

        return problem(HttpStatus.CONFLICT,
                "PAYMENT_CURRENCY_MISMATCH", ex.getMessage());
    }

    @ExceptionHandler(DuplicatePaymentReferenceException.class)
    ResponseEntity<ProblemDetail> handleDuplicateReference(
            DuplicatePaymentReferenceException ex) {

        return problem(HttpStatus.CONFLICT,
                "DUPLICATE_PAYMENT_REFERENCE", ex.getMessage());
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private static AuthenticatedActor actor(Authentication authentication) {
        CoachGymUserPrincipal principal =
                (CoachGymUserPrincipal) authentication.getPrincipal();
        return principal.authenticatedActor();
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String code, String detail) {

        return ResponseEntity
                .status(status)
                .body(ApiProblemFactory.create(status, code, detail));
    }
}
