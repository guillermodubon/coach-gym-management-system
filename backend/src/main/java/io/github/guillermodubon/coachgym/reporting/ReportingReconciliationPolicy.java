package io.github.guillermodubon.coachgym.reporting;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure reconciliation rules for organization totals and exhaustive branch partitions. */
public final class ReportingReconciliationPolicy {

    private ReportingReconciliationPolicy() {
    }

    /**
     * Requires a count total to reconcile only when the supplied branches form
     * a complete, disjoint partition of the same eligible organization set.
     */
    public static void requireCountReconciliation(
            long organizationTotal,
            Collection<Long> branchTotals,
            boolean completeBranchCoverage) {
        if (organizationTotal < 0 || branchTotals == null
                || branchTotals.stream().anyMatch(total -> total == null || total < 0)) {
            throw new IllegalArgumentException("Reporting counts must be non-negative.");
        }
        if (!completeBranchCoverage) {
            return;
        }
        long sum = 0;
        for (long total : branchTotals) {
            sum = Math.addExact(sum, total);
        }
        if (sum != organizationTotal) {
            throw new IllegalArgumentException(
                    "Complete branch totals do not reconcile with the organization total.");
        }
    }

    /**
     * Reconciles exact amounts independently per currency; currencies are
     * never converted or combined. A selected branch subset is not expected
     * to equal its organization total.
     */
    public static void requireFinancialReconciliation(
            FinancialSummary organization,
            Collection<FinancialSummary> branches,
            boolean completeBranchCoverage) {
        Objects.requireNonNull(organization, "Organization financial summary is required.");
        Objects.requireNonNull(branches, "Branch financial summaries are required.");
        if (branches.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Branch financial summaries must not contain null.");
        }
        if (!completeBranchCoverage) {
            return;
        }

        Map<String, BigDecimal> branchAmounts = new HashMap<>();
        for (FinancialSummary branch : branches) {
            for (FinancialCurrencySummary currency : branch.currencies()) {
                branchAmounts.merge(
                        currency.currency(), currency.confirmedAmount(), BigDecimal::add);
            }
        }
        Map<String, BigDecimal> organizationAmounts = amountsByCurrency(organization);
        if (!sameAmounts(organizationAmounts, branchAmounts)) {
            throw new IllegalArgumentException(
                    "Complete branch financial amounts do not reconcile per currency.");
        }

        Map<String, Long> branchCounts = new HashMap<>();
        for (FinancialSummary branch : branches) {
            for (FinancialCurrencySummary currency : branch.currencies()) {
                branchCounts.merge(currency.currency(), currency.paidCount(), Math::addExact);
            }
        }
        Map<String, Long> organizationCounts = countsByCurrency(organization);
        if (!organizationCounts.equals(branchCounts)) {
            throw new IllegalArgumentException(
                    "Complete branch payment counts do not reconcile per currency.");
        }
    }

    private static Map<String, BigDecimal> amountsByCurrency(FinancialSummary summary) {
        Map<String, BigDecimal> amounts = new HashMap<>();
        for (FinancialCurrencySummary currency : summary.currencies()) {
            amounts.put(currency.currency(), currency.confirmedAmount());
        }
        return amounts;
    }

    private static Map<String, Long> countsByCurrency(FinancialSummary summary) {
        Map<String, Long> counts = new HashMap<>();
        for (FinancialCurrencySummary currency : summary.currencies()) {
            counts.put(currency.currency(), currency.paidCount());
        }
        return counts;
    }

    private static boolean sameAmounts(
            Map<String, BigDecimal> first,
            Map<String, BigDecimal> second) {
        if (!first.keySet().equals(second.keySet())) {
            return false;
        }
        List<String> currencies = new ArrayList<>(first.keySet());
        return currencies.stream().allMatch(currency ->
                first.get(currency).compareTo(second.get(currency)) == 0);
    }
}
