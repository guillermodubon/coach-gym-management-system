package io.github.guillermodubon.coachgym.payment.web;

import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptDetails;
import io.github.guillermodubon.coachgym.payment.application.PaymentAttemptApplicationService;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** HTTP boundary for staff-created Stripe Checkout attempts. */
@RestController
@RequestMapping("/api/v1/payment-attempts")
@Tag(
        name = "Payment Attempts",
        description = "Staff operations for Stripe Test Mode payment attempts.")
@SecurityRequirement(name = "sessionCookie")
class PaymentAttemptController {

    private final PaymentAttemptApplicationService paymentAttemptService;

    PaymentAttemptController(PaymentAttemptApplicationService paymentAttemptService) {
        this.paymentAttemptService = paymentAttemptService;
    }

    @PostMapping("/stripe-checkout")
    @Operation(
            summary = "Create a Stripe Test Mode checkout",
            description = """
                    Creates a hosted Stripe Test Mode checkout for an existing membership period.
                    The amount and currency are read from the server-side membership-period
                    pricing snapshot. Only ADMIN and RECEPTIONIST may call this operation, and
                    a valid CSRF token is required. The returned URL is for the caller only.
                    """)
    @ApiResponse(responseCode = "201", description = "Checkout created")
    @ApiResponse(responseCode = "400", description = "Invalid membership selection")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions or invalid CSRF token")
    @ApiResponse(responseCode = "404", description = "Membership or period not found")
    @ApiResponse(responseCode = "409", description = "Membership relationship or state conflict")
    @ApiResponse(responseCode = "502", description = "Provider returned unusable checkout data")
    @ApiResponse(responseCode = "503", description = "Payment provider unavailable")
    ResponseEntity<StripeCheckoutResponse> createCheckout(
            @Valid @RequestBody CreateStripeCheckoutRequest request,
            Authentication authentication,
            UriComponentsBuilder uriBuilder) {

        var checkout = paymentAttemptService.createCheckout(
                request.toCommand(), actor(authentication));
        URI location = uriBuilder
                .path("/api/v1/payment-attempts/{id}")
                .buildAndExpand(checkout.attempt().id())
                .toUri();
        return ResponseEntity.created(location)
                .body(StripeCheckoutResponse.from(checkout));
    }

    @GetMapping("/{attemptId}")
    @Operation(
            summary = "Get a payment attempt",
            description = "Returns a safe payment-attempt snapshot without provider references.")
    @ApiResponse(responseCode = "200", description = "Payment attempt returned")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @ApiResponse(responseCode = "404", description = "Payment attempt not found")
    PaymentAttemptResponse findById(@PathVariable UUID attemptId) {
        return PaymentAttemptResponse.from(paymentAttemptService.findById(attemptId));
    }

    @PostMapping("/{attemptId}/cancel")
    @Operation(
            summary = "Cancel a payment attempt",
            description = """
                    Requests provider-side cancellation of an open Stripe checkout and records
                    CANCELLED only after provider confirmation. The expected version is required
                    for optimistic locking. Only ADMIN and RECEPTIONIST may call this operation,
                    and a valid CSRF token is required.
                    """)
    @ApiResponse(responseCode = "200", description = "Payment attempt cancelled")
    @ApiResponse(responseCode = "400", description = "Invalid cancellation request")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions or invalid CSRF token")
    @ApiResponse(responseCode = "404", description = "Payment attempt not found")
    @ApiResponse(responseCode = "409", description = "State or optimistic-lock conflict")
    @ApiResponse(responseCode = "503", description = "Payment provider unavailable")
    PaymentAttemptResponse cancel(
            @PathVariable UUID attemptId,
            @Valid @RequestBody CancelPaymentAttemptRequest request,
            Authentication authentication) {
        PaymentAttemptDetails cancelled = paymentAttemptService.cancel(
                request.toCommand(attemptId), actor(authentication));
        return PaymentAttemptResponse.from(cancelled);
    }

    private static AuthenticatedActor actor(Authentication authentication) {
        CoachGymUserPrincipal principal = (CoachGymUserPrincipal) authentication.getPrincipal();
        return principal.authenticatedActor();
    }
}
