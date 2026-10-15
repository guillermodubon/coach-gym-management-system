package io.github.guillermodubon.coachgym.reporting;

import java.math.BigDecimal;
import java.util.Locale;

/** Confirmed-payment total for one currency; it is never converted or cross-summed. */
public record FinancialCurrencySummary(
        String currency,
        long paidCount,
        BigDecimal confirmedAmount) {

    public FinancialCurrencySummary {
        if (currency == null || !currency.strip().matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException("Currency must be a three-letter code.");
        }
        currency = currency.strip().toUpperCase(Locale.ROOT);
        if (paidCount < 0) {
            throw new IllegalArgumentException("Paid count must not be negative.");
        }
        if (confirmedAmount == null || confirmedAmount.signum() < 0) {
            throw new IllegalArgumentException("Confirmed amount must be non-negative.");
        }
    }
}
