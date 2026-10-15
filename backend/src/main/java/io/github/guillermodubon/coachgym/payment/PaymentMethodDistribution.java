package io.github.guillermodubon.coachgym.payment;

import java.util.Locale;

/** Paid-payment count for one safe payment-method and currency bucket. */
public record PaymentMethodDistribution(
        String currency,
        PaymentMethod paymentMethod,
        long paymentCount) {

    public PaymentMethodDistribution {
        if (currency == null || !currency.strip().matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException("Payment currency must be a three-letter code.");
        }
        currency = currency.strip().toUpperCase(Locale.ROOT);
        if (paymentMethod == null || paymentCount < 0) {
            throw new IllegalArgumentException("Payment-method metric is invalid.");
        }
    }
}
