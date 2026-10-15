package io.github.guillermodubon.coachgym.payment.web;

import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import io.github.guillermodubon.coachgym.payment.application.RegisterPaymentCommand;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RegisterPaymentRequest(

        @NotNull
        UUID clientId,

        @NotNull
        UUID membershipId,

        @NotNull
        UUID membershipPeriodId,

        @NotNull
        BigDecimal amount,

        @NotNull
        String currency,

        @NotNull
        PaymentMethod paymentMethod,

        String externalReference,

        @NotNull
        Instant paidAt) {

    RegisterPaymentCommand toCommand() {
        String normalizedReference =
                (externalReference == null
                        || externalReference.isBlank())
                        ? null
                        : externalReference.trim();

        return new RegisterPaymentCommand(
                clientId,
                membershipId,
                membershipPeriodId,
                amount,
                currency,
                paymentMethod,
                normalizedReference,
                paidAt);
    }
}
