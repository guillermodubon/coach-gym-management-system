package io.github.guillermodubon.coachgym.payment.domain;

import java.math.BigDecimal;
import java.util.UUID;

public class PaymentAmountMismatchException
        extends RuntimeException {

    public PaymentAmountMismatchException(
            UUID membershipId,
            UUID membershipPeriodId,
            BigDecimal requestedAmount,
            BigDecimal expectedAmount) {

        super(
                "Payment amount "
                        + requestedAmount.toPlainString()
                        + " does not match the period final price "
                        + expectedAmount.toPlainString()
                        + " for period "
                        + membershipPeriodId
                        + " of membership "
                        + membershipId
                        + ".");
    }
}
