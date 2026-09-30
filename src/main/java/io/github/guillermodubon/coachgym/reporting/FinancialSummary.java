package io.github.guillermodubon.coachgym.reporting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bounded confirmed-revenue projection grouped by currency.
 *
 * <p>This is an operational reporting contract, not a ledger or a full
 * accounting statement.</p>
 */
public record FinancialSummary(List<FinancialCurrencySummary> currencies) {

    public FinancialSummary {
        Objects.requireNonNull(currencies, "Currency summaries are required.");
        if (currencies.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Currency summaries must not contain null.");
        }
        Set<String> codes = currencies.stream()
                .map(FinancialCurrencySummary::currency)
                .collect(Collectors.toUnmodifiableSet());
        if (codes.size() != currencies.size()) {
            throw new IllegalArgumentException("Currency summaries must be unique by currency.");
        }
        List<FinancialCurrencySummary> normalized = new ArrayList<>(currencies);
        normalized.sort(Comparator.comparing(FinancialCurrencySummary::currency));
        currencies = List.copyOf(normalized);
    }
}
