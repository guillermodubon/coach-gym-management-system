package io.github.guillermodubon.coachgym.reporting;

import java.math.BigDecimal;

final class DashboardMetricValidation {

    private DashboardMetricValidation() {
    }

    static long nonNegative(long value, String label) {
        if (value < 0) {
            throw new IllegalArgumentException(label + " must not be negative.");
        }
        return value;
    }

    static BigDecimal nonNegativeMoney(BigDecimal value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException(label + " must not be negative.");
        }
        return value;
    }

    static String currency(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Dashboard payment currency is required.");
        }
        String normalized = value.strip().toUpperCase(java.util.Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException(
                    "Dashboard payment currency must be a three-letter ISO code.");
        }
        return normalized;
    }
}
