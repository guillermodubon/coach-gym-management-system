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
        long version) {
}
