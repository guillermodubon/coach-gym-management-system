package io.github.guillermodubon.coachgym.payment.web;

import io.github.guillermodubon.coachgym.payment.application.RefundPaymentCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

record RefundPaymentRequest(
        @NotBlank
        @Size(min = 3, max = 2000)
        String reason,
        @Size(max = 128)
        String externalReference,
        @PositiveOrZero
        long version) {

    RefundPaymentCommand toCommand(UUID paymentId) {
        return new RefundPaymentCommand(
                paymentId,
                reason,
                externalReference,
                version);
    }
}
