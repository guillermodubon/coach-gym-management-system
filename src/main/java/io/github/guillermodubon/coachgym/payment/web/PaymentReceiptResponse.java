package io.github.guillermodubon.coachgym.payment.web;

import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptDetails;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Metadata response for the canonical payment receipt; never contains PDF bytes or paths. */
@Schema(description = "Immutable metadata and financial snapshot of a canonical payment receipt.")
public record PaymentReceiptResponse(
        UUID id,
        String receiptNumber,
        UUID paymentId,
        String paymentCode,
        PaymentStatus paymentStatus,
        String clientCode,
        String clientDisplayName,
        String membershipCode,
        String planName,
        String promotionName,
        int membershipPeriodNumber,
        LocalDate periodStartsOn,
        LocalDate periodEndsOn,
        BigDecimal listPrice,
        BigDecimal discountAmount,
        BigDecimal amount,
        String currency,
        PaymentMethod paymentMethod,
        Instant paidAt,
        Instant generatedAt,
        UUID generatedByUserId,
        String generatedByDisplayName,
        boolean testMode,
        String contentType,
        long sizeBytes,
        String checksumSha256,
        String rendererVersion,
        long version,
        URI downloadUrl,
        UUID branchId) {

    static PaymentReceiptResponse from(PaymentReceiptDetails details, URI downloadUrl) {
        return new PaymentReceiptResponse(
                details.id(),
                details.receiptNumber(),
                details.paymentId(),
                details.paymentCode(),
                details.paymentStatus(),
                details.clientCode(),
                details.clientDisplayName(),
                details.membershipCode(),
                details.planName(),
                details.promotionName(),
                details.membershipPeriodNumber(),
                details.periodStartsOn(),
                details.periodEndsOn(),
                details.listPrice(),
                details.discountAmount(),
                details.amount(),
                details.currency(),
                details.paymentMethod(),
                details.paidAt(),
                details.generatedAt(),
                details.generatedByUserId(),
                details.generatedByDisplayName(),
                details.testMode(),
                details.contentType(),
                details.sizeBytes(),
                details.checksumSha256(),
                details.rendererVersion(),
                details.version(),
                downloadUrl,
                details.branchId());
    }
}
