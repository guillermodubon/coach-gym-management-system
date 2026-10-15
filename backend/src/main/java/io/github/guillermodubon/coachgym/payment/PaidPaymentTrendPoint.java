package io.github.guillermodubon.coachgym.payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

/** Sparse local-calendar trend bucket for canonical paid payments. */
public record PaidPaymentTrendPoint(
        LocalDate bucketStart,
        String currency,
        long paymentCount,
        BigDecimal confirmedAmount) {

    public PaidPaymentTrendPoint {
        if (bucketStart == null) {
            throw new IllegalArgumentException("Trend bucket date is required.");
        }
        if (currency == null || !currency.strip().matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException("Payment currency must be a three-letter code.");
        }
        currency = currency.strip().toUpperCase(Locale.ROOT);
        if (paymentCount < 0 || confirmedAmount == null || confirmedAmount.signum() < 0) {
            throw new IllegalArgumentException("Payment trend values must be non-negative.");
        }
    }
}
