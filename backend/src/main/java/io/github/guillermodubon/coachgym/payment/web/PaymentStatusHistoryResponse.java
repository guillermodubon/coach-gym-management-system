package io.github.guillermodubon.coachgym.payment.web;

import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import io.github.guillermodubon.coachgym.payment.PaymentStatusHistoryDetails;
import java.time.Instant;
import java.util.UUID;

record PaymentStatusHistoryResponse(
        UUID historyId,
        UUID paymentId,
        PaymentStatus previousStatus,
        PaymentStatus newStatus,
        String reason,
        Instant changedAt,
        UUID changedByUserId) {

    static PaymentStatusHistoryResponse from(
            PaymentStatusHistoryDetails details) {
        return new PaymentStatusHistoryResponse(
                details.historyId(),
                details.paymentId(),
                details.previousStatus(),
                details.newStatus(),
                details.reason(),
                details.changedAt(),
                details.changedByUserId());
    }
}
