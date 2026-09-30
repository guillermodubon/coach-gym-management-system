package io.github.guillermodubon.coachgym.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffAuthorizationContext;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportingBlockOneContractTest {

    private static final UUID BRANCH_A = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");
    private static final UUID BRANCH_B = UUID.fromString(
            "10000000-0000-0000-0000-000000000002");
    private static final UUID BRANCH_C = UUID.fromString(
            "10000000-0000-0000-0000-000000000003");
    private static final Instant GENERATED_AT = Instant.parse("2026-09-28T12:00:00Z");

    @Test
    void normalizesBranchSetsAndRequiresSelectionToMatchItsScope() {
        BranchReportingSelection selection = BranchReportingSelection.authorizedBranches(
                List.of(BRANCH_B, BRANCH_A, BRANCH_B));

        assertThat(selection.scope()).isEqualTo(ReportingScope.AUTHORIZED_BRANCH_SET);
        assertThat(selection.branchIds()).containsExactly(BRANCH_A, BRANCH_B);
        assertThatThrownBy(() -> selection.branchIds().add(BRANCH_C))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new BranchReportingSelection(
                ReportingScope.ORGANIZATION, List.of(BRANCH_A)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BranchReportingSelection(
                ReportingScope.SINGLE_BRANCH, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BranchReportingSelection.authorizedBranches(List.of(BRANCH_A)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(BranchReportingSelection.organizationWide().branchIds()).isEmpty();
    }

    @Test
    void validatesHalfOpenRangeTimezoneAndDaylightSavingBoundaries() {
        ReportingRange springForward = ReportingRange.of(
                LocalDate.parse("2026-03-08"),
                LocalDate.parse("2026-03-09"),
                "America/New_York");

        assertThat(springForward.calendarDays()).isEqualTo(1);
        assertThat(java.time.Duration.between(
                springForward.fromInclusiveInstant(), springForward.toExclusiveInstant()))
                .hasHours(23);
        assertThatThrownBy(() -> ReportingRange.of(
                LocalDate.parse("2026-03-09"),
                LocalDate.parse("2026-03-09"),
                "UTC"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReportingRange.of(
                LocalDate.parse("2026-03-09"),
                LocalDate.parse("2026-03-10"),
                "Not/A_Timezone"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReportingRange(
                LocalDate.parse("2026-03-09"),
                LocalDate.parse("2026-03-10"),
                null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void enforcesBoundedGranularityBucketsAndExplicitResponseContext() {
        ReportingRange range = new ReportingRange(
                LocalDate.parse("2025-01-01"),
                LocalDate.parse("2026-01-02"),
                ZoneId.of("UTC"));
        ReportingRangePolicy policy = ReportingRangePolicy.defaults();

        assertThat(policy.validate(range, ReportingGranularity.DAILY)).isEqualTo(range);
        assertThat(ReportingGranularity.DAILY.bucketCount(range)).isEqualTo(366);
        assertThat(ReportingGranularity.WEEKLY.bucketCount(range))
                .isLessThanOrEqualTo(ReportingGranularity.WEEKLY.maximumBuckets());
        assertThatThrownBy(() -> policy.validate(
                new ReportingRange(
                        LocalDate.parse("2025-01-01"),
                        LocalDate.parse("2026-01-03"),
                        ZoneId.of("UTC")),
                ReportingGranularity.DAILY))
                .isInstanceOf(IllegalArgumentException.class);

        ReportingContext context = new ReportingContext(
                BranchReportingSelection.activeBranch(BRANCH_A), range, GENERATED_AT);
        assertThat(context.selection().scope()).isEqualTo(ReportingScope.ACTIVE_BRANCH);
        assertThat(context.range().timezone()).isEqualTo(ZoneId.of("UTC"));
        assertThat(context.generatedAt()).isEqualTo(GENERATED_AT);
    }

    @Test
    void groupsExactFinancialAmountsByCurrencyWithoutCrossCurrencyTotal() {
        FinancialSummary summary = new FinancialSummary(List.of(
                new FinancialCurrencySummary("eur", 1, new BigDecimal("12.345")),
                new FinancialCurrencySummary("USD", 2, new BigDecimal("100.20"))));

        assertThat(summary.currencies())
                .extracting(FinancialCurrencySummary::currency)
                .containsExactly("EUR", "USD");
        assertThat(summary.currencies().getFirst().confirmedAmount())
                .isEqualByComparingTo("12.345");
        assertThatThrownBy(() -> summary.currencies().add(
                new FinancialCurrencySummary("CAD", 0, BigDecimal.ZERO)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new FinancialSummary(List.of(
                new FinancialCurrencySummary("usd", 1, BigDecimal.ONE),
                new FinancialCurrencySummary("USD", 2, BigDecimal.TEN))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FinancialCurrencySummary(
                "USD", 0, new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconcilesCountsAndExactFinancialValuesOnlyForCompleteBranchPartitions() {
        ReportingReconciliationPolicy.requireCountReconciliation(
                10, List.of(4L, 6L), true);
        ReportingReconciliationPolicy.requireCountReconciliation(
                10, List.of(4L), false);
        assertThatThrownBy(() -> ReportingReconciliationPolicy.requireCountReconciliation(
                10, List.of(4L, 5L), true))
                .isInstanceOf(IllegalArgumentException.class);

        FinancialSummary organization = new FinancialSummary(List.of(
                new FinancialCurrencySummary("USD", 3, new BigDecimal("30.00")),
                new FinancialCurrencySummary("EUR", 1, new BigDecimal("5.00"))));
        FinancialSummary branchA = new FinancialSummary(List.of(
                new FinancialCurrencySummary("USD", 1, new BigDecimal("10.00")),
                new FinancialCurrencySummary("EUR", 1, new BigDecimal("5.00"))));
        FinancialSummary branchB = new FinancialSummary(List.of(
                new FinancialCurrencySummary("USD", 2, new BigDecimal("20.00"))));

        ReportingReconciliationPolicy.requireFinancialReconciliation(
                organization, List.of(branchA, branchB), true);
        ReportingReconciliationPolicy.requireFinancialReconciliation(
                organization, List.of(branchA), false);
        assertThatThrownBy(() -> ReportingReconciliationPolicy.requireFinancialReconciliation(
                organization,
                List.of(branchA, new FinancialSummary(List.of(
                        new FinancialCurrencySummary("USD", 1, new BigDecimal("19.99"))))),
                true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static StaffAuthorizationContext organizationAdmin() {
        return actor(RoleCode.ADMIN, StaffScopeType.ORGANIZATION,
                StaffAccountStatus.ACTIVE, Set.of(BRANCH_A, BRANCH_B));
    }

    private static StaffAuthorizationContext branchAdmin() {
        return actor(RoleCode.ADMIN, StaffScopeType.BRANCH,
                StaffAccountStatus.ACTIVE, Set.of(BRANCH_A, BRANCH_B));
    }

    private static StaffAuthorizationContext receptionist() {
        return actor(RoleCode.RECEPTIONIST, StaffScopeType.BRANCH,
                StaffAccountStatus.ACTIVE, Set.of(BRANCH_A, BRANCH_B));
    }

    private static StaffAuthorizationContext actor(
            RoleCode role,
            StaffScopeType scope,
            StaffAccountStatus status,
            Set<UUID> branches) {
        return new StaffAuthorizationContext(
                UUID.randomUUID(), Set.of(role), status, scope, branches);
    }
}
