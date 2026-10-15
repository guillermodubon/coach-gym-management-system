package io.github.guillermodubon.coachgym.reporting.application;

import java.util.Set;

/** Raised when effective paid data contains incompatible currencies. */
public class DashboardCurrencyConflictException extends RuntimeException {

    private final Set<String> currencies;

    public DashboardCurrencyConflictException(Set<String> currencies) {
        super("Paid dashboard data contains currencies that cannot be combined.");
        this.currencies = currencies == null ? Set.of() : Set.copyOf(currencies);
    }

    public Set<String> currencies() {
        return currencies;
    }
}
