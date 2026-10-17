package io.github.guillermodubon.coachgym.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.client.ClientReportingQuery;
import io.github.guillermodubon.coachgym.client.ClientReportingSummary;
import io.github.guillermodubon.coachgym.membership.MembershipReportingQuery;
import io.github.guillermodubon.coachgym.membership.MembershipReportingSummary;
import io.github.guillermodubon.coachgym.payment.PaidPaymentTrendPoint;
import io.github.guillermodubon.coachgym.payment.PaymentFinancialMetrics;
import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import io.github.guillermodubon.coachgym.payment.PaymentMethodDistribution;
import io.github.guillermodubon.coachgym.payment.PaymentReportingQuery;
import io.github.guillermodubon.coachgym.payment.PaymentTrendGranularity;
import io.github.guillermodubon.coachgym.plan.MembershipPlanBranchCoverageScope;
import io.github.guillermodubon.coachgym.shared.ReportingQueryScope;
import io.github.guillermodubon.coachgym.shared.ReportingQueryWindow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ReportingAggregateQueriesIntegrationTest extends AbstractDashboardApiIntegrationTest {

    private static final ZoneId REPORT_ZONE = ZoneId.of("America/New_York");
    private static final LocalDate FROM = LocalDate.of(2099, 9, 1);
    private static final LocalDate UNTIL = LocalDate.of(2099, 10, 1);
    private static final LocalDate AS_OF = LocalDate.of(2099, 9, 15);
    @Autowired private PaymentReportingQuery paymentReportingQuery;
    @Autowired private ClientReportingQuery clientReportingQuery;
    @Autowired private MembershipReportingQuery membershipReportingQuery;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void aggregatesReconcileAcrossOrganizationBranchAndAuthorizedSetWithoutMultiplication()
            throws Exception {
        ReportingQueryWindow window = new ReportingQueryWindow(FROM, UNTIL, REPORT_ZONE);
        ReportingQueryScope organization = ReportingQueryScope.organization();
        PaymentFinancialMetrics financialBaseline =
                paymentReportingQuery.summarize(organization, window);
        ClientReportingSummary clientBaseline =
                clientReportingQuery.summarize(organization, window);
        MembershipReportingSummary membershipBaseline =
                membershipReportingQuery.summarize(organization, window, AS_OF);

        UUID branchA = UUID.randomUUID();
        UUID branchB = UUID.randomUUID();
        UUID clientA = UUID.randomUUID();
        UUID clientB = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID membershipA = UUID.randomUUID();
        UUID membershipB = UUID.randomUUID();
        UUID oldPeriodA = UUID.randomUUID();
        UUID currentPeriodA = UUID.randomUUID();
        UUID currentPeriodB = UUID.randomUUID();
        String planCodeSnapshot = "RPT" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase(java.util.Locale.ROOT);

        TransactionTemplate fixtureTransaction = new TransactionTemplate(transactionManager);
        fixtureTransaction.executeWithoutResult(status -> {
            insertBranch(branchA, "Reporting Branch A");
            insertBranch(branchB, "Reporting Branch B");
            insertClient(clientA, branchA, "ACTIVE", at(FROM, 10, 0));
            insertClient(clientB, branchB, "INACTIVE", at(FROM.plusDays(1), 10, 0));
            insertPlan(planId);
            insertMembership(membershipA, clientA, branchA, "ACTIVE", at(FROM.plusDays(1), 11, 0));
            insertMembership(membershipB, clientB, branchB, "FROZEN", at(FROM.plusDays(2), 11, 0));
            insertPeriod(oldPeriodA, membershipA, planId, branchA, 1,
                    LocalDate.of(2099, 8, 1), LocalDate.of(2099, 9, 5),
                    at(LocalDate.of(2099, 8, 1), 10, 0), planCodeSnapshot);
            insertPeriod(currentPeriodA, membershipA, planId, branchA, 2,
                    LocalDate.of(2099, 9, 6), LocalDate.of(2099, 9, 20),
                    at(FROM, 12, 0), planCodeSnapshot);
            insertPeriod(currentPeriodB, membershipB, planId, branchB, 1,
                    FROM, LocalDate.of(2099, 9, 25),
                    at(FROM.plusDays(2), 12, 0), planCodeSnapshot);
            insertCoverage(oldPeriodA, "SINGLE_BRANCH", List.of(branchA));
            insertCoverage(currentPeriodA, "ALL_BRANCHES", List.of(branchA, branchB));
            insertCoverage(currentPeriodB, "SELECTED_BRANCHES", List.of(branchA, branchB));

            insertPayment(
                    clientA, membershipA, currentPeriodA, branchA,
                    "100.00", "USD", "CASH", "PAID", window.fromInclusiveInstant());
            insertPayment(
                    clientB, membershipB, currentPeriodB, branchB,
                    "50.00", "USD", "CARD", "PAID", at(FROM.plusDays(14), 12, 0));
            insertPayment(
                    clientB, membershipB, currentPeriodB, branchB,
                    "70.00", "EUR", "BANK_TRANSFER", "PAID", at(FROM.plusDays(14), 13, 0));
            UUID voided = insertPayment(
                    clientA, membershipA, currentPeriodA, branchA,
                    "40.00", "USD", "CASH", "VOIDED", at(FROM.plusDays(3), 9, 0));
            insertPaymentHistory(voided, "VOIDED", at(FROM.plusDays(10), 9, 0));
            UUID refunded = insertPayment(
                    clientB, membershipB, currentPeriodB, branchB,
                    "30.00", "USD", "CARD", "REFUNDED", at(FROM.plusDays(4), 9, 0));
            insertPaymentHistory(refunded, "REFUNDED", at(FROM.plusDays(11), 9, 0));
            insertRefund(refunded, "30.00", "USD", "CARD", at(FROM.plusDays(11), 9, 0));
            insertPayment(
                    clientA, membershipA, currentPeriodA, branchA,
                    "999.00", "USD", "CASH", "PAID", window.toExclusiveInstant());
            insertAttempt(clientA, membershipA, currentPeriodA, branchA);
        });

        ReportingQueryScope bothBranches = ReportingQueryScope.branches(List.of(branchB, branchA));
        ReportingQueryScope onlyBranchA = ReportingQueryScope.branches(List.of(branchA));
        ReportingQueryScope onlyBranchB = ReportingQueryScope.branches(List.of(branchB));

        PaymentFinancialMetrics organizationFinancial =
                paymentReportingQuery.summarize(organization, window);
        PaymentFinancialMetrics branchSetFinancial =
                paymentReportingQuery.summarize(bothBranches, window);
        assertThat(organizationFinancial).isEqualTo(branchSetFinancial);
        assertThat(currency(organizationFinancial, "USD"))
                .satisfies(usd -> {
                    assertThat(usd.paidCount()).isEqualTo(2);
                    assertThat(usd.confirmedAmount()).isEqualByComparingTo("150.00");
                    assertThat(usd.averagePaidAmount()).isEqualByComparingTo("75.00");
                    assertThat(usd.voidedCount()).isEqualTo(1);
                    assertThat(usd.voidedAmount()).isEqualByComparingTo("40.00");
                    assertThat(usd.refundedCount()).isEqualTo(1);
                    assertThat(usd.refundedAmount()).isEqualByComparingTo("30.00");
                });
        assertThat(currency(organizationFinancial, "EUR"))
                .satisfies(eur -> {
                    assertThat(eur.paidCount()).isEqualTo(1);
                    assertThat(eur.confirmedAmount()).isEqualByComparingTo("70.00");
                });
        assertThat(paymentReportingQuery.summarize(onlyBranchA, window).currencies())
                .satisfiesExactly(usd -> assertThat(usd.confirmedAmount())
                        .isEqualByComparingTo("100.00"));
        assertThat(paymentReportingQuery.paymentMethodDistribution(bothBranches, window))
                .containsExactly(
                        new PaymentMethodDistribution("EUR", PaymentMethod.BANK_TRANSFER, 1),
                        new PaymentMethodDistribution("USD", PaymentMethod.CARD, 1),
                        new PaymentMethodDistribution("USD", PaymentMethod.CASH, 1));

        List<PaidPaymentTrendPoint> daily = paymentReportingQuery.paidTrend(
                bothBranches, window, PaymentTrendGranularity.DAILY);
        assertThat(daily).hasSize(3)
                .anySatisfy(point -> {
                    assertThat(point.bucketStart()).isEqualTo(FROM);
                    assertThat(point.currency()).isEqualTo("USD");
                    assertThat(point.confirmedAmount()).isEqualByComparingTo("100.00");
                });
        assertThat(paymentReportingQuery.paidTrend(
                bothBranches, window, PaymentTrendGranularity.WEEKLY)).hasSize(3);
        assertThat(paymentReportingQuery.paidTrend(
                bothBranches, window, PaymentTrendGranularity.MONTHLY)).hasSize(2);

        ClientReportingSummary clients = clientReportingQuery.summarize(bothBranches, window);
        assertThat(clients)
                .isEqualTo(new ClientReportingSummary(2, 1, 1, 2));
        assertThat(clientReportingQuery.summarize(onlyBranchA, window))
                .isEqualTo(new ClientReportingSummary(1, 1, 0, 1));
        ClientReportingSummary organizationClients =
                clientReportingQuery.summarize(organization, window);
        assertThat(organizationClients.totalClients() - clientBaseline.totalClients())
                .isEqualTo(clients.totalClients());
        assertThat(organizationClients.activeClients() - clientBaseline.activeClients())
                .isEqualTo(clients.activeClients());
        assertThat(organizationClients.inactiveClients() - clientBaseline.inactiveClients())
                .isEqualTo(clients.inactiveClients());
        assertThat(organizationClients.registeredInRange() - clientBaseline.registeredInRange())
                .isEqualTo(clients.registeredInRange());

        MembershipReportingSummary memberships =
                membershipReportingQuery.summarize(bothBranches, window, AS_OF);
        assertThat(memberships.activeMemberships()).isEqualTo(1);
        assertThat(memberships.frozenMemberships()).isEqualTo(1);
        assertThat(memberships.expiredMemberships()).isZero();
        assertThat(memberships.cancelledMemberships()).isZero();
        assertThat(memberships.newMemberships()).isEqualTo(2);
        assertThat(memberships.activePeriods()).isEqualTo(1);
        assertThat(memberships.newPeriods()).isEqualTo(2);
        assertThat(memberships.expiringPeriods()).isEqualTo(1);
        assertThat(memberships.planDistribution())
                .containsExactly(new MembershipReportingSummary.PlanDistribution(
                        planCodeSnapshot, "Reporting Plan Snapshot", 2));
        assertThat(memberships.coverageDistribution())
                .containsExactly(
                        new MembershipReportingSummary.CoverageDistribution(
                                MembershipPlanBranchCoverageScope.ALL_BRANCHES, 1),
                        new MembershipReportingSummary.CoverageDistribution(
                                MembershipPlanBranchCoverageScope.SELECTED_BRANCHES, 1));
        assertThat(membershipReportingQuery.summarize(onlyBranchA, window, AS_OF).activePeriods())
                .isEqualTo(1);
        assertThat(membershipReportingQuery.summarize(onlyBranchB, window, AS_OF).activePeriods())
                .isZero();

        MembershipReportingSummary organizationMemberships =
                membershipReportingQuery.summarize(organization, window, AS_OF);
        assertThat(organizationMemberships.activeMemberships()
                - membershipBaseline.activeMemberships()).isEqualTo(memberships.activeMemberships());
        assertThat(organizationMemberships.frozenMemberships()
                - membershipBaseline.frozenMemberships()).isEqualTo(memberships.frozenMemberships());
        assertThat(organizationMemberships.newMemberships()
                - membershipBaseline.newMemberships()).isEqualTo(memberships.newMemberships());
        assertThat(organizationMemberships.activePeriods()
                - membershipBaseline.activePeriods()).isEqualTo(memberships.activePeriods());
        assertThat(organizationMemberships.newPeriods()
                - membershipBaseline.newPeriods()).isEqualTo(memberships.newPeriods());
        assertThat(organizationMemberships.expiringPeriods()
                - membershipBaseline.expiringPeriods()).isEqualTo(memberships.expiringPeriods());
        assertThat(organizationMemberships.planDistribution())
                .contains(new MembershipReportingSummary.PlanDistribution(
                        planCodeSnapshot, "Reporting Plan Snapshot", 2));
        assertThat(membershipBaseline.planDistribution())
                .doesNotContain(new MembershipReportingSummary.PlanDistribution(
                        planCodeSnapshot, "Reporting Plan Snapshot", 2));
        assertThat(financialBaseline.currencies()).isEmpty();
    }

    @Test
    void emptyBranchScopeReturnsZeroMetricsAndNoDistributions() {
        ReportingQueryScope emptyBranch = ReportingQueryScope.branches(List.of(UUID.randomUUID()));
        ReportingQueryWindow window = new ReportingQueryWindow(FROM, UNTIL, REPORT_ZONE);

        assertThat(paymentReportingQuery.summarize(emptyBranch, window).currencies()).isEmpty();
        assertThat(paymentReportingQuery.paymentMethodDistribution(emptyBranch, window)).isEmpty();
        assertThat(paymentReportingQuery.paidTrend(
                emptyBranch, window, PaymentTrendGranularity.DAILY)).isEmpty();
        assertThat(clientReportingQuery.summarize(emptyBranch, window))
                .isEqualTo(new ClientReportingSummary(0, 0, 0, 0));

        MembershipReportingSummary memberships =
                membershipReportingQuery.summarize(emptyBranch, window, AS_OF);
        assertThat(memberships.activeMemberships()).isZero();
        assertThat(memberships.newMemberships()).isZero();
        assertThat(memberships.activePeriods()).isZero();
        assertThat(memberships.newPeriods()).isZero();
        assertThat(memberships.expiringPeriods()).isZero();
        assertThat(memberships.planDistribution()).isEmpty();
        assertThat(memberships.coverageDistribution()).isEmpty();
    }

    @Test
    void representativeBranchFiltersUseExistingOwnershipIndexes() {
        LocalDate planFrom = LocalDate.of(2098, 9, 1);
        LocalDate planUntil = LocalDate.of(2098, 10, 1);
        UUID branchId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UUID periodId = UUID.randomUUID();
        UUID[] correctionPaymentIds = new UUID[2];
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            insertBranch(branchId, "Reporting Plan Branch");
            insertClient(clientId, branchId, "ACTIVE", at(planFrom, 10, 0));
            insertPlan(planId);
            insertMembership(
                    membershipId, clientId, branchId, "ACTIVE", at(planFrom, 11, 0));
            insertPeriod(
                    periodId,
                    membershipId,
                    planId,
                    branchId,
                    1,
                    planFrom,
                    LocalDate.of(2098, 9, 30),
                    at(planFrom, 12, 0),
                    "RPT" + UUID.randomUUID().toString().replace("-", "")
                            .substring(0, 12).toUpperCase(java.util.Locale.ROOT));
            insertCoverage(periodId, "ALL_BRANCHES", List.of(branchId));
            insertPayment(
                    clientId, membershipId, periodId, branchId,
                    "40.00", "USD", "CASH", "PAID", at(planFrom.plusDays(1), 10, 0));
            correctionPaymentIds[0] = insertPayment(
                    clientId, membershipId, periodId, branchId,
                    "30.00", "USD", "CASH", "VOIDED", at(planFrom.plusDays(2), 10, 0));
            insertPaymentHistory(
                    correctionPaymentIds[0], "VOIDED", at(planFrom.plusDays(3), 10, 0));
            correctionPaymentIds[1] = insertPayment(
                    clientId, membershipId, periodId, branchId,
                    "20.00", "USD", "CARD", "REFUNDED", at(planFrom.plusDays(2), 11, 0));
            insertPaymentHistory(
                    correctionPaymentIds[1], "REFUNDED", at(planFrom.plusDays(4), 10, 0));
            insertRefund(
                    correctionPaymentIds[1], "20.00", "USD", "CARD",
                    at(planFrom.plusDays(4), 10, 0));

            // Verify index eligibility, not production cardinality or cost estimates.
            jdbcTemplate.execute("set local enable_seqscan = off");

            assertPlanUsesIndex("""
                    explain (analyze, buffers, costs false)
                    select id from gym.payments
                    where registered_at_branch_id = ? and status = 'PAID'
                      and paid_at >= ? and paid_at < ?
                    """, "idx_payments_branch_status_paid_at", branchId, planFrom, planUntil);
            assertPlanUsesIndex("""
                    explain (analyze, buffers, costs false)
                    select id from gym.clients
                    where home_branch_id = ? and created_at >= ? and created_at < ?
                    """, "idx_clients_home_branch_status", branchId, planFrom, planUntil);
            assertPlanUsesIndex("""
                    explain (analyze, buffers, costs false)
                    select id from gym.memberships
                    where registered_at_branch_id = ? and status = 'ACTIVE'
                      and created_at >= ? and created_at < ?
                    """, "idx_memberships_registered_at_branch_status", branchId, planFrom, planUntil);
            assertPlanUsesIndex("""
                    explain (analyze, buffers, costs false)
                    select id from gym.membership_periods
                    where registered_at_branch_id = ? and created_at >= ? and created_at < ?
                    """, "idx_membership_periods_registered_at_branch_membership",
                    branchId, planFrom, planUntil);
            assertPlanUsesIndex("""
                    explain (analyze, buffers, costs false)
                    select id from gym.payment_status_history
                    where payment_id = ? and previous_status = 'PAID' and new_status = 'VOIDED'
                      and occurred_at >= ? and occurred_at < ?
                    """, "idx_payment_status_history_payment_occurred_at",
                    correctionPaymentIds[0], planFrom, planUntil);
            assertPlanUsesIndex("""
                    explain (analyze, buffers, costs false)
                    select id from gym.payment_refunds
                    where payment_id = ? and refunded_at >= ? and refunded_at < ?
                    """, "idx_payment_refunds_refunded_at",
                    correctionPaymentIds[1], planFrom, planUntil);
        });
    }

    private void assertPlanUsesIndex(
            String explainSql,
            String indexName,
            UUID indexedIdentifier,
            LocalDate fromInclusive,
            LocalDate toExclusive) {
        List<String> plan = jdbcTemplate.queryForList(
                explainSql,
                String.class,
                indexedIdentifier,
                OffsetDateTime.ofInstant(fromInclusive.atStartOfDay(REPORT_ZONE).toInstant(), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(toExclusive.atStartOfDay(REPORT_ZONE).toInstant(), ZoneOffset.UTC));

        String executionPlan = String.join("\n", plan);
        assertThat(executionPlan).contains(indexName, "actual time=", "Buffers:");
    }

    private static PaymentFinancialMetrics.CurrencyMetrics currency(
            PaymentFinancialMetrics metrics,
            String currency) {
        return metrics.currencies().stream()
                .filter(value -> value.currency().equals(currency))
                .findFirst()
                .orElseThrow();
    }

    private void insertBranch(UUID branchId, String name) {
        jdbcTemplate.update("""
                insert into gym.gym_branches
                    (id, organization_id, code, name, timezone, status, is_initial_branch)
                select ?, organization.id, ?, ?, 'America/New_York', 'ACTIVE', false
                from gym.organizations organization
                where organization.is_canonical
                """, branchId, branchCode(branchId), name);
    }

    private void insertClient(UUID clientId, UUID branchId, String status, Instant createdAt) {
        jdbcTemplate.update("""
                insert into gym.clients
                    (id, first_name, last_name, phone, status, home_branch_id,
                     created_at, deactivated_at)
                values (?, 'Report', 'Fixture', '+15550000000', ?, ?, ?, ?)
                """, clientId, status, branchId, offset(createdAt),
                "INACTIVE".equals(status) ? offset(createdAt) : null);
    }

    private void insertPlan(UUID planId) {
        jdbcTemplate.update("""
                insert into gym.membership_plans
                    (id, name, duration_value, duration_unit, list_price, currency,
                     branch_coverage_scope)
                values (?, 'Reporting Plan', 1, 'MONTH', 100.00, 'USD', 'ALL_BRANCHES')
                """, planId);
    }

    private void insertMembership(
            UUID membershipId,
            UUID clientId,
            UUID branchId,
            String status,
            Instant createdAt) {
        jdbcTemplate.update("""
                insert into gym.memberships
                    (id, client_id, status, created_by_user_id,
                     registered_at_branch_id, created_at)
                values (?, ?, ?, ?, ?, ?)
                """, membershipId, clientId, status, adminId, branchId, offset(createdAt));
    }

    private void insertPeriod(
            UUID periodId,
            UUID membershipId,
            UUID planId,
            UUID branchId,
            int periodNumber,
            LocalDate startsOn,
            LocalDate effectiveEndsOn,
            Instant createdAt,
            String planCodeSnapshot) {
        String source = periodNumber == 1 ? "INITIAL" : "RENEWAL";
        jdbcTemplate.update("""
                insert into gym.membership_periods (
                    id, membership_id, period_number, period_source, membership_plan_id,
                    plan_code_snapshot, plan_name_snapshot, duration_value_snapshot,
                    duration_unit_snapshot, list_price, currency, discount_amount,
                    final_price, starts_on, base_ends_on, effective_ends_on,
                    created_by_user_id, registered_at_branch_id, created_at)
                values (?, ?, ?, ?, ?, ?, 'Reporting Plan Snapshot', 1, 'MONTH',
                        100.00, 'USD', 0.00, 100.00, ?, ?, ?, ?, ?, ?)
                """, periodId, membershipId, periodNumber, source, planId,
                planCodeSnapshot, startsOn, effectiveEndsOn, effectiveEndsOn,
                adminId, branchId, offset(createdAt));
    }

    private void insertCoverage(UUID periodId, String scope, List<UUID> branchIds) {
        jdbcTemplate.update("""
                insert into gym.membership_period_coverage_snapshots
                    (membership_period_id, coverage_scope_snapshot, source_plan_version)
                values (?, ?, 0)
                """, periodId, scope);
        for (UUID branchId : branchIds) {
            jdbcTemplate.update("""
                    insert into gym.membership_period_branch_coverage
                        (membership_period_id, branch_id)
                    values (?, ?)
                    """, periodId, branchId);
        }
    }

    private UUID insertPayment(
            UUID clientId,
            UUID membershipId,
            UUID periodId,
            UUID branchId,
            String amount,
            String currency,
            String method,
            String status,
            Instant paidAt) {
        UUID paymentId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.payments
                    (id, client_id, membership_id, membership_period_id, amount,
                     currency, payment_method, status, paid_at, registered_by_user_id,
                     registered_at_branch_id)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, paymentId, clientId, membershipId, periodId,
                new BigDecimal(amount), currency, method, status, offset(paidAt), adminId, branchId);
        return paymentId;
    }

    private void insertPaymentHistory(UUID paymentId, String targetStatus, Instant occurredAt) {
        jdbcTemplate.update("""
                insert into gym.payment_status_history
                    (id, payment_id, previous_status, new_status, reason,
                     occurred_at, changed_by_user_id)
                values (?, ?, 'PAID', ?, 'Reporting aggregate fixture', ?, ?)
                """, UUID.randomUUID(), paymentId, targetStatus, offset(occurredAt), adminId);
    }

    private void insertRefund(
            UUID paymentId,
            String amount,
            String currency,
            String method,
            Instant refundedAt) {
        jdbcTemplate.update("""
                insert into gym.payment_refunds
                    (id, payment_id, amount, currency, refund_method, reason,
                     refunded_at, refunded_by_user_id)
                values (?, ?, ?, ?, ?, 'Reporting aggregate fixture', ?, ?)
                """, UUID.randomUUID(), paymentId, new BigDecimal(amount), currency,
                method, offset(refundedAt), adminId);
    }

    private void insertAttempt(UUID clientId, UUID membershipId, UUID periodId, UUID branchId) {
        jdbcTemplate.update("""
                insert into gym.payment_attempts
                    (id, client_id, membership_id, membership_period_id, provider,
                     status, expected_amount, currency, created_by_user_id,
                     initiated_at_branch_id)
                values (?, ?, ?, ?, 'STRIPE', 'CREATED', 9999.00, 'USD', ?, ?)
                """, UUID.randomUUID(), clientId, membershipId, periodId, adminId, branchId);
    }

    private static Instant at(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(REPORT_ZONE).toInstant();
    }

    private static OffsetDateTime offset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static String branchCode(UUID id) {
        return "RPT" + id.toString().replace("-", "").substring(0, 12).toUpperCase(java.util.Locale.ROOT);
    }
}
