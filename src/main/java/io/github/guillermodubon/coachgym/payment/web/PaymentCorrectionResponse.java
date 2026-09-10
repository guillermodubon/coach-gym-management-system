package io.github.guillermodubon.coachgym.payment.web;

import io.github.guillermodubon.coachgym.payment.PaymentCorrectionDetails;
import io.github.guillermodubon.coachgym.payment.PaymentCorrectionType;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import java.time.Instant;
import java.util.UUID;

record PaymentCorrectionResponse(
        UUID paymentId,
        String paymentCode,
        PaymentCorrectionType correctionType,
        PaymentStatus previousStatus,
        PaymentStatus currentStatus,
        String reason,
        Instant correctedAt,
        UUID correctedByUserId,
        long version,
        PaymentRefundResponse refund) {

    static PaymentCorrectionResponse from(PaymentCorrectionDetails details) {
        return new PaymentCorrectionResponse(
                details.paymentId(),
                details.paymentCode(),
                details.correctionType(),
                details.previousStatus(),
                details.currentStatus(),
                details.reason(),
                details.correctedAt(),
                details.correctedByUserId(),
                details.version(),
                PaymentRefundResponse.from(details.refund()));
    }
}
