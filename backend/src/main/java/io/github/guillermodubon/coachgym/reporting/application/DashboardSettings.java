package io.github.guillermodubon.coachgym.reporting.application;

import java.util.Locale;

/** Business settings required to calculate operational dashboard metrics. */
public record DashboardSettings(
        int membershipExpirationWarningDays,
        String currency) {

    public DashboardSettings {
        if (membershipExpirationWarningDays < 0
                || membershipExpirationWarningDays > 90) {
            throw new ReportingValidationException(
                    "Membership expiration warning days must be between 0 and 90.");
        }
        if (currency == null || currency.isBlank()) {
            throw new ReportingValidationException(
                    "Dashboard currency is required.");
        }
        currency = currency.strip().toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) {
            throw new ReportingValidationException(
                    "Dashboard currency must be a three-letter ISO code.");
        }
    }
}
