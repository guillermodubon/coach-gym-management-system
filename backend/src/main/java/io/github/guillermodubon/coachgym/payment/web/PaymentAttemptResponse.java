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
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID clientId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID membershipId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID membershipPeriodId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        PaymentProvider provider,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        PaymentAttemptStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal expectedAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String currency,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        PaymentAttemptFailureCode failureCode,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        UUID confirmedPaymentId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID createdByUserId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        Instant completedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
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
