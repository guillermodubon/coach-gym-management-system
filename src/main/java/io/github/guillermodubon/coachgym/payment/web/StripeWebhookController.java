package io.github.guillermodubon.coachgym.payment.web;

import io.github.guillermodubon.coachgym.payment.PaymentProvider;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderEventApplicationService;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderEventProcessingResult;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderException;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderFailureCode;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderWebhookLimits;
import io.github.guillermodubon.coachgym.payment.application.PaymentProviderWebhookVerifier;
import io.github.guillermodubon.coachgym.payment.application.VerifiedPaymentProviderEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exact anonymous Stripe webhook boundary; signature verification precedes event processing. */
@RestController
@RequestMapping("/api/v1/payment-provider/stripe/webhook")
@Tag(
        name = "Payment Provider Webhooks",
        description = "Verified provider notifications for Stripe Test Mode.")
class StripeWebhookController {

    private final ObjectProvider<PaymentProviderWebhookVerifier> verifierProvider;
    private final PaymentProviderEventApplicationService eventService;
    private final PaymentProviderWebhookLimits requestLimits;

    StripeWebhookController(
            ObjectProvider<PaymentProviderWebhookVerifier> verifierProvider,
            PaymentProviderEventApplicationService eventService,
            PaymentProviderWebhookLimits requestLimits) {
        this.verifierProvider = Objects.requireNonNull(verifierProvider);
        this.eventService = Objects.requireNonNull(eventService);
        this.requestLimits = Objects.requireNonNull(requestLimits);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Receive a Stripe webhook",
            description = """
                    Accepts a raw Stripe provider notification. No user session is required,
                    but the Stripe-Signature header must verify with the configured Test Mode
                    secret before any payment state is changed. Duplicate verified events are
                    acknowledged idempotently. The raw body and signature are never persisted
                    or returned.
                    """)
    @ApiResponse(responseCode = "204", description = "Notification accepted or already processed")
    @ApiResponse(responseCode = "400", description = "Invalid signature, payload, or request size")
    @ApiResponse(responseCode = "503", description = "Provider verification unavailable")
    @ApiResponse(responseCode = "500", description = "Retryable processing failure")
    @SecurityRequirement(name = "stripeWebhookSignature")
    ResponseEntity<Void> receive(
            @RequestBody
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Raw Stripe JSON notification; it is verified and discarded after processing.",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "object")))
            byte[] rawPayload,
            @Parameter(
                    name = "Stripe-Signature",
                    required = true,
                    description = "Stripe signature used for request authentication.")
            @RequestHeader(value = "Stripe-Signature", required = false)
            String signatureHeader) {

        enforceLimits(rawPayload, signatureHeader);
        PaymentProviderWebhookVerifier verifier = verifierProvider.getIfAvailable();
        if (verifier == null) {
            throw new PaymentProviderException(PaymentProviderFailureCode.UNAVAILABLE);
        }
        VerifiedPaymentProviderEvent verified = verifier.verify(
                PaymentProvider.STRIPE, rawPayload, signatureHeader);
        PaymentProviderEventProcessingResult result = eventService.process(verified);
        if (result == null) {
            throw new IllegalStateException("Provider event processing result was missing.");
        }
        return ResponseEntity.noContent().build();
    }

    private void enforceLimits(byte[] rawPayload, String signatureHeader) {
        if (rawPayload == null || rawPayload.length > requestLimits.maxPayloadBytes()) {
            throw new PaymentWebhookPayloadTooLargeException();
        }
        if (signatureHeader != null
                && signatureHeader.length() > requestLimits.maxSignatureHeaderLength()) {
            throw new PaymentWebhookSignatureTooLargeException();
        }
    }
}
