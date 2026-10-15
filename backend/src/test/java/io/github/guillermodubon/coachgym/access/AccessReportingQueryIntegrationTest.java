package io.github.guillermodubon.coachgym.access;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.shared.ReportingQueryScope;
import io.github.guillermodubon.coachgym.shared.ReportingQueryWindow;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class AccessReportingQueryIntegrationTest extends AbstractAccessApiIntegrationTest {

    private static final ZoneId REPORT_ZONE = ZoneId.of("America/New_York");
    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate UNTIL = LocalDate.of(2026, 11, 3);
    private static final Instant INCLUSIVE = Instant.parse("2026-11-01T04:00:00Z");
    private static final Instant DAY_TWO = Instant.parse("2026-11-02T05:00:00Z");
    private static final Instant EXCLUSIVE = Instant.parse("2026-11-03T05:00:00Z");

    @Autowired private AccessReportingQuery accessReportingQuery;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void reconcilesOrganizationAndPhysicalBranchCountsAcrossFallDstBoundary() {
        UUID branchA = initialBranchId();
        UUID branchB = insertBranch();
        ClientFixture qrClient = createClient("ACTIVE");
        UUID credentialId = insertCredential(qrClient);

        List<AccessReasonCode> denialReasons = List.of(
                AccessReasonCode.IDENTIFIER_NOT_FOUND,
                AccessReasonCode.CLIENT_INACTIVE,
                AccessReasonCode.MEMBERSHIP_NOT_FOUND,
                AccessReasonCode.MEMBERSHIP_NOT_STARTED,
                AccessReasonCode.MEMBERSHIP_PERIOD_EXPIRED,
                AccessReasonCode.MEMBERSHIP_FROZEN,
                AccessReasonCode.MEMBERSHIP_EXPIRED,
                AccessReasonCode.MEMBERSHIP_CANCELLED,
                AccessReasonCode.MEMBERSHIP_NOT_VALID_AT_BRANCH,
                AccessReasonCode.PAYMENT_REQUIRED,
                AccessReasonCode.ACCESS_CREDENTIAL_INVALID,
                AccessReasonCode.DUPLICATE_CHECK_IN);
        for (int index = 0; index < denialReasons.size(); index++) {
            insertAttempt(index % 2 == 0 ? branchA : branchB,
                    "CLIENT_CODE", "DENIED", denialReasons.get(index).name(),
                    Instant.parse("2026-11-01T16:00:00Z").plusSeconds(index));
        }
        insertAttempt(branchA, "UNKNOWN", "ALLOWED", "ACCESS_ALLOWED", INCLUSIVE);
        insertQrAttempt(branchB, qrClient.id(), credentialId, DAY_TWO);
        insertAttempt(branchB, "CLIENT_CODE", "ALLOWED", "ACCESS_ALLOWED", EXCLUSIVE);

        ReportingQueryWindow window = new ReportingQueryWindow(FROM, UNTIL, REPORT_ZONE);
        assertThat(window.fromInclusiveInstant()).isEqualTo(INCLUSIVE);
        assertThat(window.toExclusiveInstant()).isEqualTo(EXCLUSIVE);

        AccessReportingSummary organization = accessReportingQuery.summarize(
                ReportingQueryScope.organization(), window);
        assertThat(organization.totalAttempts()).isEqualTo(14);
        assertThat(organization.allowedAttempts()).isEqualTo(2);
        assertThat(organization.deniedAttempts()).isEqualTo(12);
        assertThat(organization.allowedRate()).isEqualByComparingTo("0.1429");
        assertThat(organization.manualAttempts()).isEqualTo(12);
        assertThat(organization.qrAttempts()).isEqualTo(1);
        assertThat(organization.unknownSourceAttempts()).isEqualTo(1);
        assertThat(organization.denialReasonCounts()).hasSize(12)
                .allSatisfy((reason, count) -> assertThat(count).isEqualTo(1));
        assertThat(organization.denialReasonCounts())
                .containsEntry(AccessReasonCode.DUPLICATE_CHECK_IN, 1L)
                .containsEntry(AccessReasonCode.PAYMENT_REQUIRED, 1L)
                .containsEntry(AccessReasonCode.MEMBERSHIP_NOT_VALID_AT_BRANCH, 1L);
        assertThat(organization.toString())
                .doesNotContain("private", "credential", "token", "fingerprint", "example.test");

        List<AccessBranchReportingSummary> branches = accessReportingQuery.summarizeByBranch(
                ReportingQueryScope.branches(List.of(branchB, branchA)), window);
        assertThat(branches).hasSize(2);
        assertThat(branches.stream().mapToLong(AccessBranchReportingSummary::totalAttempts).sum())
                .isEqualTo(organization.totalAttempts());
        assertThat(branches.stream().mapToLong(AccessBranchReportingSummary::allowedAttempts).sum())
                .isEqualTo(organization.allowedAttempts());
        assertThat(branches.stream().mapToLong(AccessBranchReportingSummary::deniedAttempts).sum())
                .isEqualTo(organization.deniedAttempts());
        assertThat(branches).anySatisfy(branch -> {
            assertThat(branch.branchId()).isEqualTo(branchA);
            assertThat(branch.totalAttempts()).isEqualTo(7);
            assertThat(branch.unknownSourceAttempts()).isEqualTo(1);
        });
        assertThat(branches).anySatisfy(branch -> {
            assertThat(branch.branchId()).isEqualTo(branchB);
            assertThat(branch.totalAttempts()).isEqualTo(7);
            assertThat(branch.qrAttempts()).isEqualTo(1);
        });

        var daily = accessReportingQuery.dailyTrend(ReportingQueryScope.organization(), window);
        assertThat(daily).hasSize(2);
        assertThat(daily.get(0).day()).isEqualTo(FROM);
        assertThat(daily.get(0).totalAttempts()).isEqualTo(13);
        assertThat(daily.get(1).day()).isEqualTo(FROM.plusDays(1));
        assertThat(daily.get(1).totalAttempts()).isEqualTo(1);
        assertThat(daily.stream().mapToLong(AccessDailyTrendPoint::totalAttempts).sum())
                .isEqualTo(organization.totalAttempts());
    }

    @Test
    void emptyOrganizationReturnsEverySafeDenialBucketAndZeroDailyValues() {
        ReportingQueryWindow window = new ReportingQueryWindow(FROM, UNTIL, REPORT_ZONE);

        AccessReportingSummary summary = accessReportingQuery.summarize(
                ReportingQueryScope.organization(), window);

        assertThat(summary.totalAttempts()).isZero();
        assertThat(summary.allowedAttempts()).isZero();
        assertThat(summary.deniedAttempts()).isZero();
        assertThat(summary.allowedRate()).isEqualByComparingTo("0.0000");
        assertThat(summary.denialReasonCounts()).hasSize(12)
                .allSatisfy((reason, count) -> assertThat(count).isZero());
        AccessOperationalDaySummary operationalDay = accessReportingQuery.summarizeOperationalDay(
                ReportingQueryScope.organization(), FROM, REPORT_ZONE);
        assertThat(operationalDay.totalAttempts()).isZero();
        assertThat(operationalDay.allowedAttempts()).isZero();
        assertThat(operationalDay.deniedAttempts()).isZero();
        assertThat(accessReportingQuery.dailyTrend(ReportingQueryScope.organization(), window))
                .extracting(AccessDailyTrendPoint::totalAttempts)
                .containsExactly(0L, 0L);
    }

    @Test
    void operationalDayProjectionContainsOnlyDecisionCountsForOneBranch() {
        UUID branchA = initialBranchId();
        UUID branchB = insertBranch();
        insertAttempt(branchA, "CLIENT_CODE", "DENIED", "PAYMENT_REQUIRED", INCLUSIVE);
        insertAttempt(branchA, "CLIENT_CODE", "ALLOWED", "ACCESS_ALLOWED", INCLUSIVE.plusSeconds(1));
        insertAttempt(branchB, "CLIENT_CODE", "DENIED", "DUPLICATE_CHECK_IN", INCLUSIVE.plusSeconds(2));
        insertAttempt(branchA, "CLIENT_CODE", "ALLOWED", "ACCESS_ALLOWED", DAY_TWO.minusMillis(1));
        insertAttempt(branchA, "CLIENT_CODE", "DENIED", "DUPLICATE_CHECK_IN", DAY_TWO);

        AccessOperationalDaySummary summary = accessReportingQuery.summarizeOperationalDay(
                ReportingQueryScope.branches(List.of(branchA)), FROM, REPORT_ZONE);

        assertThat(summary.day()).isEqualTo(FROM);
        assertThat(summary.timezone()).isEqualTo(REPORT_ZONE);
        assertThat(summary.totalAttempts()).isEqualTo(3);
        assertThat(summary.allowedAttempts()).isEqualTo(2);
        assertThat(summary.deniedAttempts()).isEqualTo(1);
        assertThat(summary.toString())
                .doesNotContain("PAYMENT_REQUIRED", "CLIENT_CODE", "DUPLICATE_CHECK_IN");
    }

    @Test
    void currentBranchAndTimeIndexesAreEligibleForAccessAggregateFilter() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.execute("set local enable_seqscan = off");
            List<String> plan = jdbcTemplate.queryForList("""
                    explain (analyze, buffers, costs false)
                    select id from gym.access_records
                    where branch_id = ? and occurred_at >= ? and occurred_at < ?
                    """, String.class, initialBranchId(),
                    OffsetDateTime.ofInstant(INCLUSIVE, ZoneOffset.UTC),
                    OffsetDateTime.ofInstant(EXCLUSIVE, ZoneOffset.UTC));
            assertThat(String.join("\n", plan))
                    .contains("idx_access_records_branch_source_result_occurred_at",
                            "actual time=", "Buffers:");
        });
    }

    private UUID insertBranch() {
        UUID branchId = UUID.randomUUID();
        String code = "RPT-" + branchId.toString().replace("-", "")
                .substring(0, 12).toUpperCase(java.util.Locale.ROOT);
        jdbcTemplate.update("""
                insert into gym.gym_branches
                    (id, organization_id, code, name, timezone, status, is_initial_branch)
                select ?, organization.id, ?, 'Access reporting branch', 'America/New_York',
                       'ACTIVE', false
                from gym.organizations organization where organization.is_canonical
                """, branchId, code);
        return branchId;
    }

    private UUID insertCredential(ClientFixture client) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.access_credentials
                    (id, client_id, credential_code, token_fingerprint,
                     token_scheme_version, payload_version, status, issued_at,
                     issued_by_user_id, storage_key, content_type, size_bytes,
                     checksum_sha256, renderer_version)
                values (?, ?, ?, ?, 'sha256-v1', 'v1', 'ACTIVE', current_timestamp,
                        ?, ?, 'image/png', 32, ?, 'qr-v1')
                """, id, client.id(), "REPORT-QR-" + id, "a".repeat(64),
                userId(ADMIN_USERNAME), "access-credentials/" + id + ".png", "b".repeat(64));
        return id;
    }

    private void insertAttempt(
            UUID branchId, String source, String decision, String reason, Instant occurredAt) {
        jdbcTemplate.update("""
                insert into gym.access_records
                    (id, entered_code, decision, reason_code, details, occurred_at,
                     recorded_by_user_id, identification_source, branch_id)
                values (?, 'MANUAL-REPORT-FIXTURE', ?, ?, 'Reporting fixture', ?, ?, ?, ?)
                """, UUID.randomUUID(), decision, reason,
                OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC),
                userId(ADMIN_USERNAME), source, branchId);
    }

    private void insertQrAttempt(
            UUID branchId, UUID clientId, UUID credentialId, Instant occurredAt) {
        jdbcTemplate.update("""
                insert into gym.access_records
                    (id, entered_code, client_id, decision, reason_code, details,
                     client_code_snapshot, occurred_at, recorded_by_user_id, identification_source,
                     access_credential_id, branch_id)
                values (?, 'QR_CREDENTIAL', ?, 'ALLOWED', 'ACCESS_ALLOWED',
                        'Reporting QR fixture', ?, ?, ?, 'QR_CREDENTIAL', ?, ?)
                """, UUID.randomUUID(), clientId,
                jdbcTemplate.queryForObject(
                        "select client_code from gym.clients where id = ?", String.class, clientId),
                OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC),
                userId(ADMIN_USERNAME), credentialId, branchId);
    }
}
