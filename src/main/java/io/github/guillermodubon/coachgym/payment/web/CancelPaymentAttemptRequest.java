package io.github.guillermodubon.coachgym.payment.web;

import io.github.guillermodubon.coachgym.payment.application.CancelPaymentAttemptCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

/** Optimistic-lock version supplied by staff when cancelling an attempt. */
@Schema(description = "Concurrency token required to cancel an open payment attempt.")
record CancelPaymentAttemptRequest(

        @NotNull
        @PositiveOrZero
        @Schema(description = "Current payment-attempt version.", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        Long version) {

    CancelPaymentAttemptCommand toCommand(UUID paymentAttemptId) {
        return new CancelPaymentAttemptCommand(paymentAttemptId, version);
    }
}
