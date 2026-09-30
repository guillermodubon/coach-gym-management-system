package io.github.guillermodubon.coachgym.payment;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Current-state financial aggregates grouped by currency, never cross-summed. */
public record PaymentFinancialMetrics(List<CurrencyMetrics> currencies) {

    public PaymentFinancialMetrics {
        Objects.requireNonNull(currencies, "Payment currency metrics are required.");
        if (currencies.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Payment currency metrics must not contain null.");
        }
        Set<String> codes = currencies.stream()
                .map(CurrencyMetrics::currency)
                .collect(Collectors.toUnmodifiableSet());
        if (codes.size() != currencies.size()) {
            throw new IllegalArgumentException("Payment currency metrics must be unique by currency.");
        }
        List<CurrencyMetrics> sorted = new ArrayList<>(currencies);
        sorted.sort(Comparator.comparing(CurrencyMetrics::currency));
        currencies = List.copyOf(sorted);
    }

    public record CurrencyMetrics(
            String currency,
            long paidCount,
            BigDecimal confirmedAmount,
            BigDecimal averagePaidAmount,
            long voidedCount,
            BigDecimal voidedAmount,
            long refundedCount,
            BigDecimal refundedAmount) {

        public CurrencyMetrics {
            if (currency == null || !currency.strip().matches("[A-Za-z]{3}")) {
                throw new IllegalArgumentException("Payment currency must be a three-letter code.");
            }
            currency = currency.strip().toUpperCase(Locale.ROOT);
            nonNegative(paidCount, "Paid payment count");
            nonNegative(voidedCount, "Voided payment count");
            nonNegative(refundedCount, "Refunded payment count");
            confirmedAmount = nonNegative(confirmedAmount, "Confirmed payment amount");
            averagePaidAmount = nonNegative(averagePaidAmount, "Average paid amount");
            voidedAmount = nonNegative(voidedAmount, "Voided payment amount");
            refundedAmount = nonNegative(refundedAmount, "Refunded payment amount");
            if (paidCount == 0 && (confirmedAmount.signum() != 0 || averagePaidAmount.signum() != 0)) {
                throw new IllegalArgumentException("Empty paid population must have zero amount metrics.");
            }
        }

        private static BigDecimal nonNegative(BigDecimal amount, String label) {
            if (amount == null || amount.signum() < 0) {
                throw new IllegalArgumentException(label + " must be non-negative.");
            }
            return amount;
        }

        private static void nonNegative(long value, String label) {
            if (value < 0) {
                throw new IllegalArgumentException(label + " must be non-negative.");
            }
        }
    }
}
