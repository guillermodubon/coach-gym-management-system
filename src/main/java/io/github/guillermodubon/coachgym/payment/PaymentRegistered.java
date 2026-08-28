package io.github.guillermodubon.coachgym.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event published after a payment has been persisted successfully.
 *
 * <p>{@code externalReference} is intentionally absent — audit
 * consumers must not store sensitive reference values. Use
 * {@code hasExternalReference} instead.</p>
 */
public record PaymentRegistered(
        UUID paymentId,
        String paymentCode,
        UUID clientId,
        UUID membershipId,
        UUID membershipPeriodId,
        BigDecimal amount,
        String currency,
        PaymentMethod paymentMethod,
        boolean hasExternalReference,
        Instant paidAt,
        PaymentStatus resultingStatus,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt) {
}
