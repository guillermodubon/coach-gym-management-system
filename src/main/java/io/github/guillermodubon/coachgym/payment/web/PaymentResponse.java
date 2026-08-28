package io.github.guillermodubon.coachgym.payment.web;

import io.github.guillermodubon.coachgym.payment.PaymentDetails;
import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
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

    static PaymentResponse from(PaymentDetails details) {
        return new PaymentResponse(
                details.id(),
                details.paymentCode(),
                details.clientId(),
                details.membershipId(),
                details.membershipPeriodId(),
                details.amount(),
                details.currency(),
                details.paymentMethod(),
                details.status(),
                details.externalReference(),
                details.paidAt(),
                details.registeredByUserId(),
                details.createdAt(),
                details.updatedAt(),
                details.version());
    }
}
