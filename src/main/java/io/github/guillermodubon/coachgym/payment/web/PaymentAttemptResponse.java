package io.github.guillermodubon.coachgym.payment.web;

import io.github.guillermodubon.coachgym.payment.PaymentAttemptDetails;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptFailureCode;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptStatus;
import io.github.guillermodubon.coachgym.payment.PaymentProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Safe HTTP projection of a payment attempt; provider references are intentionally omitted. */
@Schema(description = "Payment-attempt state and server-owned pricing snapshot.")
record PaymentAttemptResponse(
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
        UUID initiatedAtBranchId) {

    static PaymentAttemptResponse from(PaymentAttemptDetails details) {
        return new PaymentAttemptResponse(
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
                details.initiatedAtBranchId());
    }
}
