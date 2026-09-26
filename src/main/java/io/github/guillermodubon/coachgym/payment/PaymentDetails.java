package io.github.guillermodubon.coachgym.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentDetails(
        UUID id,
        String paymentCode,
        UUID clientId,
        UUID membershipId,
        UUID membershipPeriodId,
        BigDecimal amount,
        String currency,
        PaymentMethod paymentMethod,
        PaymentStatus status,
        String externalReference,
        Instant paidAt,
        UUID registeredByUserId,
        Instant createdAt,
        Instant updatedAt,
        long version,
        UUID registeredAtBranchId) {

    /** Compatibility constructor for callers created before branch scoping. */
    public PaymentDetails(
            UUID id,
            String paymentCode,
            UUID clientId,
            UUID membershipId,
            UUID membershipPeriodId,
            BigDecimal amount,
            String currency,
            PaymentMethod paymentMethod,
            PaymentStatus status,
            String externalReference,
            Instant paidAt,
            UUID registeredByUserId,
            Instant createdAt,
            Instant updatedAt,
            long version) {
        this(id, paymentCode, clientId, membershipId, membershipPeriodId, amount,
                currency, paymentMethod, status, externalReference, paidAt,
                registeredByUserId, createdAt, updatedAt, version, null);
    }
}
