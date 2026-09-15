package io.github.guillermodubon.coachgym.reporting;

import java.math.BigDecimal;

/** Effective paid revenue for the requested reporting period. */
public record PaymentDashboardDetails(
        long paidCount,
        BigDecimal registeredAmount,
        String currency) {

    public PaymentDashboardDetails {
        DashboardMetricValidation.nonNegative(paidCount, "Paid payment count");
        registeredAmount = DashboardMetricValidation.nonNegativeMoney(
                registeredAmount, "Registered payment amount");
        currency = DashboardMetricValidation.currency(currency);
    }
}
