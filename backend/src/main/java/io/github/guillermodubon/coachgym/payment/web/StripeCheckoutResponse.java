package io.github.guillermodubon.coachgym.payment.web;

import io.github.guillermodubon.coachgym.payment.PaymentAttemptDetails;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptFailureCode;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptStatus;
import io.github.guillermodubon.coachgym.payment.PaymentProvider;
import io.github.guillermodubon.coachgym.payment.application.PaymentAttemptCheckoutDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/** Staff-facing checkout response with the one-time redirect URL. */
@Schema(description = "Created Stripe Test Mode checkout and its payment-attempt snapshot.")
record StripeCheckoutResponse(
        UUID id,
        UUID clientId,
        UUID membershipId,
        UUID membershipPeriodId,
        PaymentProvider provider,
        PaymentAttemptStatus status,
        BigDecimal expectedAmount,
        String currency,
        PaymentAttemptFailureCode failureCode,
        UUID confirmedPaymentId,
        UUID createdByUserId,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        long version,
        URI checkoutUrl,
        Instant expiresAt,
        @Schema(description = "Always true for this Test Mode endpoint.", allowableValues = {"true"})
        boolean sandbox) {

    static StripeCheckoutResponse from(PaymentAttemptCheckoutDetails checkout) {
        PaymentAttemptDetails details = checkout.attempt();
        return new StripeCheckoutResponse(
                details.id(),
                details.clientId(),
                details.membershipId(),
                details.membershipPeriodId(),
                details.provider(),
                details.status(),
                details.expectedAmount(),
                details.currency(),
                details.failureCode(),
                details.confirmedPaymentId(),
                details.createdByUserId(),
                details.createdAt(),
                details.updatedAt(),
                details.completedAt(),
                details.version(),
                checkout.checkoutUrl(),
                checkout.expiresAt(),
                true);
    }
}
