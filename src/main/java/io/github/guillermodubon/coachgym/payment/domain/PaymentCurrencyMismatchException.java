package io.github.guillermodubon.coachgym.payment.domain;

import java.util.UUID;

public class PaymentCurrencyMismatchException
        extends RuntimeException {

    public PaymentCurrencyMismatchException(
            UUID membershipId,
            UUID membershipPeriodId,
            String requestedCurrency,
            String expectedCurrency) {

        super(
                "Payment currency "
                        + requestedCurrency
                        + " does not match the period currency "
                        + expectedCurrency
                        + " for period "
                        + membershipPeriodId
                        + " of membership "
                        + membershipId
                        + ".");
    }
}
