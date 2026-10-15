package io.github.guillermodubon.coachgym.payment.web;

import io.github.guillermodubon.coachgym.payment.application.VoidPaymentCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

record VoidPaymentRequest(
        @NotBlank
        @Size(min = 3, max = 2000)
        String reason,
        @PositiveOrZero
        long version) {

    VoidPaymentCommand toCommand(UUID paymentId) {
        return new VoidPaymentCommand(paymentId, reason, version);
    }
}
