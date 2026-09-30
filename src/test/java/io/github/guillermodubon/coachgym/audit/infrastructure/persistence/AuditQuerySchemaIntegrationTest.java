package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.audit.AuditEntryDetails;
import io.github.guillermodubon.coachgym.audit.AuditEntryPage;
import io.github.guillermodubon.coachgym.audit.AuditExportDataAccessException;
import io.github.guillermodubon.coachgym.audit.AuditExportLimitExceededException;
import io.github.guillermodubon.coachgym.audit.AuditExportPolicy;
import io.github.guillermodubon.coachgym.audit.AuditExportQuery;
import io.github.guillermodubon.coachgym.audit.AuditExportRow;
import io.github.guillermodubon.coachgym.audit.AuditExportValidationException;
import io.github.guillermodubon.coachgym.audit.AuditSearchQuery;
import io.github.guillermodubon.coachgym.audit.AuditVisibilityScope;
import io.github.guillermodubon.coachgym.audit.application.AuditQueryDataAccessException;
import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.springframework.beans.factory.annotation.Autowired;

class AuditQuerySchemaIntegrationTest extends AbstractIncidentApiIntegrationTest {

    private static final UUID INITIAL_BRANCH_ID = UUID.fromString(
            "7b0bf7d5-5184-43d2-8f9a-200000000002");
    private static final UUID FIRST_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000b01");
    private static final UUID SECOND_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000b02");
    private static final UUID THIRD_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000b03");
    private static final UUID CLIENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000b11");
    private static final UUID PAYMENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000b12");
    private static final UUID CORRELATION_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000b13");

    private static final Instant TIE_TIMESTAMP =
            Instant.parse("2026-09-16T12:00:00.123456Z");
    private static final Instant EARLIER_TIMESTAMP =
            Instant.parse("2026-09-15T12:00:00Z");

    @Autowired
    private JdbcAuditEntryQueryAdapter queryAdapter;

    @BeforeEach
    void clearAuditFixtures() {
        jdbcTemplate.update("delete from gym.audit_entries");
        insertAuditEntry(
                SECOND_ID,
                "AUDIT-ADMIN",
                "PAYMENT_REGISTERED",
                "PAYMENT",
                PAYMENT_ID,
                "PAY-000002",
                "Payment registered.",
                "{\"paymentId\":\"%s\",\"token\":\"secret\"}"
                        .formatted(PAYMENT_ID),
                CORRELATION_ID,
                TIE_TIMESTAMP);
        insertAuditEntry(
                FIRST_ID,
                "AUDIT-ADMIN",
                "CLIENT_REGISTERED",
                "CLIENT",
                CLIENT_ID,
                "CLI-000001",
                "Client registered.",
                "{}",
                null,
                TIE_TIMESTAMP);
        insertAuditEntry(
                THIRD_ID,
                "other-staff",
                "ACCESS_DENIED",
                "ACCESS_RECORD",
                UUID.fromString("00000000-0000-0000-0000-000000000b14"),
                "ACCESS-000003",
                "Gym access denied.",
                "{\"result\":\"DENIED\",\"reasonCode\":\"IDENTIFIER_NOT_FOUND\"}",
                null,
                EARLIER_TIMESTAMP);
    }

    @Test
    void retainsTheExistingNewestFirstAuditIndex() {
        String definition = jdbcTemplate.queryForObject("""
                select indexdef
                from pg_indexes
                where schemaname = 'gym'
                  and tablename = 'audit_entries'
                  and indexname = 'idx_audit_entries_occurred_at_id'
                """, String.class);

        assertThat(definition.toLowerCase())
                .contains("occurred_at", "id", "desc")
                .doesNotContain("metadata");
    }

    @Test
    void branchScopedListQueryUsesTheDedicatedPartialExpressionIndex(TestReporter reporter) {
        UUID otherBranch = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into gym.audit_entries
                    (id, actor_user_id, actor_identifier_snapshot, action_code,
                     resource_type, resource_id, resource_code_snapshot, summary,
                     metadata, correlation_id, occurred_at)
                select gen_random_uuid(), ?, 'audit-plan-fixture', 'ACCESS_DENIED',
                       'ACCESS_RECORD', gen_random_uuid(), 'PLAN-' || item,
                       'Bounded query-plan fixture',
                       jsonb_build_object('branchId', case when item % 20 = 0 then ? else ? end),
                       null, current_timestamp - make_interval(secs => item)
                from generate_series(1, 60000) item
                """, adminId, INITIAL_BRANCH_ID.toString(), otherBranch.toString());
        jdbcTemplate.execute("analyze gym.audit_entries");

        List<String> planLines = jdbcTemplate.queryForList("""
                explain (analyze, buffers, costs false)
                select id
                from gym.audit_entries
                where metadata ->> 'branchId' is not null
                  and metadata ->> 'branchId' = ?
                order by occurred_at desc, id desc
                limit 100
                """, String.class, INITIAL_BRANCH_ID.toString());
        String plan = String.join("\n", planLines);
        reporter.publishEntry("branchAuditQueryPlan", plan);

        assertThat(plan)
                .contains("idx_audit_entries_branch_occurred_at_id", "actual time", "Buffers:")
                .doesNotContain("Seq Scan on audit_entries");
        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                from gym.audit_entries
                where metadata ->> 'branchId' = ?
                """, Long.class, INITIAL_BRANCH_ID.toString()))
                .isEqualTo(3000L);

        jdbcTemplate.update("delete from gym.audit_entries where action_code = 'ACCESS_DENIED'");
        insertAuditEntry(
                THIRD_ID, "other-staff", "ACCESS_DENIED", "ACCESS_RECORD",
                UUID.fromString("00000000-0000-0000-0000-000000000b14"),
                "ACCESS-000003", "Gym access denied.",
                "{\"result\":\"DENIED\",\"reasonCode\":\"IDENTIFIER_NOT_FOUND\"}",
                null, EARLIER_TIMESTAMP);
    }

    @Test
    void branchScopedIndexDefinitionMatchesTheAuthorizedSqlPredicate() {
        String definition = jdbcTemplate.queryForObject("""
                select indexdef
                from pg_indexes
                where schemaname = 'gym'
                  and tablename = 'audit_entries'
                  and indexname = 'idx_audit_entries_branch_occurred_at_id'
                """, String.class);

        assertThat(definition.toLowerCase())
                .contains("(metadata ->> 'branchid'::text)", "occurred_at desc", "id desc")
                .contains("where ((metadata ->> 'branchid'::text) is not null)");
    }

    @Test
    void listsNewestFirstWithStableIdTieBreakingAndAccurateCount() {
        AuditEntryPage page = queryAdapter.findAll(
                AuditSearchQuery.defaults(), AuditVisibilityScope.organization());

        assertThat(page.items()).extracting(item -> item.id())
                .containsExactly(SECOND_ID, FIRST_ID, THIRD_ID);
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.items().getFirst().occurredAt())
                .isEqualTo(TIE_TIMESTAMP);
    }

    @Test
    void paginatesEqualTimestampsWithoutDuplicateOrSkippedRows() {
        AuditSearchQuery firstPage = new AuditSearchQuery(
                null, null, null, null, null, null, null, null, null,
                0, 1, null, null);
        AuditSearchQuery secondPage = new AuditSearchQuery(
                null, null, null, null, null, null, null, null, null,
                1, 1, null, null);

        assertThat(queryAdapter.findAll(
                firstPage, AuditVisibilityScope.organization()).items())
                .extracting(item -> item.id())
                .containsExactly(SECOND_ID);
        assertThat(queryAdapter.findAll(
                secondPage, AuditVisibilityScope.organization()).items())
                .extracting(item -> item.id())
                .containsExactly(FIRST_ID);
    }

    @Test
    void appliesExactActorActionResourceCorrelationAndInclusiveDateFilters() {
        AuditSearchQuery filtered = new AuditSearchQuery(
                adminId,
                " AUDIT-ADMIN ",
                " payment_registered ",
                " payment ",
                PAYMENT_ID,
                "PAY-000002",
                CORRELATION_ID,
                TIE_TIMESTAMP,
                TIE_TIMESTAMP,
                0,
                25,
                null,
                null);

        AuditEntryPage page = queryAdapter.findAll(
                filtered, AuditVisibilityScope.organization());

        assertThat(page.items()).extracting(item -> item.id())
                .containsExactly(SECOND_ID);
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void appliesCaseInsensitiveActorIdentifierAndRejectsNoLiveJoinEnrichment() {
        AuditSearchQuery filtered = new AuditSearchQuery(
                null,
                "AUDIT-ADMIN",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                25,
                null,
                null);

        assertThat(queryAdapter.findAll(
                filtered, AuditVisibilityScope.organization()).items())
                .extracting(item -> item.id())
                .containsExactly(SECOND_ID, FIRST_ID);
    }

    @Test
    void mapsFoundDetailWithPrecisionAndSanitizedMetadata() {
        AuditEntryDetails detail = queryAdapter.findById(
                SECOND_ID, AuditVisibilityScope.organization()).orElseThrow();

        assertThat(detail.occurredAt()).isEqualTo(TIE_TIMESTAMP);
        assertThat(detail.metadata().values())
                .containsEntry("paymentId", PAYMENT_ID.toString())
                .doesNotContainKey("token");
        assertThat(detail.metadataRedacted()).isTrue();
    }

    @Test
    void branchAndResultFiltersReconcileInSqlAndDetailsRecheckVisibility() {
        UUID assignedBranchEntry = UUID.randomUUID();
        UUID otherBranchEntry = UUID.randomUUID();
        UUID assignedBranchId = INITIAL_BRANCH_ID;
        UUID otherBranchId = UUID.randomUUID();
        UUID assignedResourceId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        insertAuditEntry(
                assignedBranchEntry,
                "AUDIT-ADMIN",
                "ACCESS_DENIED",
                "ACCESS_RECORD",
                assignedResourceId,
                "ACCESS-ASSIGNED",
                "Access denied at assigned branch.",
                "{\"branchId\":\"%s\",\"result\":\"DENIED\",\"reasonCode\":\"PAYMENT_REQUIRED\"}"
                        .formatted(assignedBranchId),
                correlationId,
                TIE_TIMESTAMP);
        insertAuditEntry(
                otherBranchEntry,
                "AUDIT-ADMIN",
                "ACCESS_DENIED",
                "ACCESS_RECORD",
                UUID.randomUUID(),
                "ACCESS-OTHER",
                "Access denied at another branch.",
                "{\"branchId\":\"%s\",\"result\":\"DENIED\"}"
                        .formatted(otherBranchId),
                null,
                TIE_TIMESTAMP);

        AuditSearchQuery query = AuditSearchQuery.from(
                adminId, " AUDIT-ADMIN ", "ACCESS_DENIED", "ACCESS_RECORD", "denied",
                assignedResourceId, "ACCESS-ASSIGNED", correlationId,
                java.util.Set.of(assignedBranchId), TIE_TIMESTAMP, TIE_TIMESTAMP,
                0, 1, "OCCURRED_AT", "DESC");
        AuditVisibilityScope assignedScope = AuditVisibilityScope.branches(
                java.util.Set.of(assignedBranchId));

        AuditEntryPage page = queryAdapter.findAll(query, assignedScope);

        assertThat(page.items()).extracting(item -> item.id())
                .containsExactly(assignedBranchEntry);
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(1);
        assertThat(queryAdapter.findById(assignedBranchEntry, assignedScope)).isPresent();
        assertThat(queryAdapter.findById(otherBranchEntry, assignedScope)).isEmpty();
        assertThat(queryAdapter.findById(THIRD_ID, assignedScope)).isEmpty();

        AuditSearchQuery noAllowedOutcome = AuditSearchQuery.from(
                null, null, null, null, "allowed", null, null, null,
                java.util.Set.of(assignedBranchId), null, null, 0, 25, null, null);
        assertThat(queryAdapter.findAll(noAllowedOutcome, assignedScope).totalElements())
                .isZero();
    }

    @Test
    void absentDetailIsEmptyAndQueriesDoNotWriteAuditRows() {
        long before = jdbcTemplate.queryForObject(
                "select count(*) from gym.audit_entries", Long.class);

        assertThat(queryAdapter.findById(
                UUID.randomUUID(), AuditVisibilityScope.organization())).isEmpty();
        queryAdapter.findAll(
                AuditSearchQuery.defaults(), AuditVisibilityScope.organization());

        long after = jdbcTemplate.queryForObject(
                "select count(*) from gym.audit_entries", Long.class);
        assertThat(after).isEqualTo(before);
    }

    @Test
    void malformedJsonbMetadataFailsClosedWithoutExposingItsValue() {
        UUID malformedId = UUID.fromString(
                "00000000-0000-0000-0000-000000000b99");
        insertAuditEntry(
                malformedId,
                "AUDIT-ADMIN",
                "PAYMENT_REGISTERED",
                "PAYMENT",
                UUID.randomUUID(),
                "PAY-MALFORMED",
                "Malformed metadata.",
                "\"not-a-metadata-object\"",
                null,
                TIE_TIMESTAMP);

        long before = jdbcTemplate.queryForObject(
                "select count(*) from gym.audit_entries", Long.class);

        assertThatThrownBy(() -> queryAdapter.findById(
                malformedId, AuditVisibilityScope.organization()))
                .isInstanceOf(AuditQueryDataAccessException.class)
                .hasMessage("Audit entries could not be read.");

        long after = jdbcTemplate.queryForObject(
                "select count(*) from gym.audit_entries", Long.class);
        assertThat(after).isEqualTo(before);
    }

    @Test
    void streamsNewestFirstWithStableAscendingIdTieBreakingAndSanitizedMetadata() {
        List<AuditExportRow> rows = new ArrayList<>();

        queryAdapter.streamExport(
                exportQuery(
                        null,
                        Instant.parse("2026-09-15T00:00:00Z"),
                        Instant.parse("2026-09-16T23:59:59Z"),
                        null,
                        null),
                new AuditExportPolicy(Duration.ofDays(3), 3),
                AuditVisibilityScope.organization(),
                rows::add);

        assertThat(rows).extracting(AuditExportRow::entryId)
                .containsExactly(FIRST_ID, SECOND_ID, THIRD_ID);
        assertThat(rows.get(1).metadata().values())
                .containsEntry("paymentId", PAYMENT_ID.toString())
                .doesNotContainKey("token");
    }

    @Test
    void streamsAscendingOrderAndAppliesEveryApprovedFilter() {
        List<AuditExportRow> rows = new ArrayList<>();

        queryAdapter.streamExport(
                exportQuery(
                        adminId,
                        TIE_TIMESTAMP,
                        TIE_TIMESTAMP,
                        "ASC",
                        "PAYMENT_REGISTERED"),
                new AuditExportPolicy(Duration.ofDays(1), 3),
                AuditVisibilityScope.organization(),
                rows::add);

        assertThat(rows).extracting(AuditExportRow::entryId)
                .containsExactly(SECOND_ID);
        assertThat(rows.getFirst())
                .extracting(
                        AuditExportRow::actorUserId,
                        AuditExportRow::actorIdentifier,
                        AuditExportRow::actionCode,
                        AuditExportRow::resourceType,
                        AuditExportRow::resourceId,
                        AuditExportRow::resourceCode,
                        AuditExportRow::correlationId)
                .containsExactly(
                        adminId,
                        "audit-admin",
                        "PAYMENT_REGISTERED",
                        "PAYMENT",
                        PAYMENT_ID,
                        "PAY-000002",
                        CORRELATION_ID);
    }

    @Test
    void branchExportAppliesPersistedBranchAndResultFiltersInSql() {
        UUID branchId = INITIAL_BRANCH_ID;
        UUID otherBranchId = UUID.randomUUID();
        UUID selectedDeniedId = UUID.randomUUID();
        UUID selectedAllowedId = UUID.randomUUID();
        UUID otherBranchDeniedId = UUID.randomUUID();
        insertAuditEntry(
                selectedDeniedId, "branch-staff", "ACCESS_DENIED", "ACCESS_RECORD",
                UUID.randomUUID(), "ACCESS-SELECTED-DENIED", "Denied at selected branch.",
                "{\"branchId\":\"%s\",\"result\":\"DENIED\",\"reasonCode\":\"IDENTIFIER_NOT_FOUND\"}"
                        .formatted(branchId),
                null, TIE_TIMESTAMP);
        insertAuditEntry(
                selectedAllowedId, "branch-staff", "CLIENT_REGISTERED", "CLIENT",
                UUID.randomUUID(), "CLI-SELECTED-ALLOWED", "Client at selected branch.",
                "{\"branchId\":\"%s\",\"result\":\"ALLOWED\"}".formatted(branchId),
                null, TIE_TIMESTAMP);
        insertAuditEntry(
                otherBranchDeniedId, "branch-staff", "ACCESS_DENIED", "ACCESS_RECORD",
                UUID.randomUUID(), "ACCESS-OTHER-DENIED", "Denied at other branch.",
                "{\"branchId\":\"%s\",\"result\":\"DENIED\",\"reasonCode\":\"IDENTIFIER_NOT_FOUND\"}"
                        .formatted(otherBranchId),
                null, TIE_TIMESTAMP);

        AuditExportQuery query = AuditExportQuery.from(
                null, null, null, null, "DENIED", null, null, null,
                java.util.Set.of(branchId),
                Instant.parse("2026-09-16T00:00:00Z"),
                Instant.parse("2026-09-16T23:59:59Z"),
                null, null);
        List<AuditExportRow> rows = new ArrayList<>();

        queryAdapter.streamExport(query, new AuditExportPolicy(Duration.ofDays(1), 10),
                AuditVisibilityScope.branches(java.util.Set.of(branchId)), rows::add);

        assertThat(rows).extracting(AuditExportRow::entryId)
                .containsExactly(selectedDeniedId);
        assertThat(rows.getFirst().branchId()).isEqualTo(branchId);
    }

    @Test
    void exactConfiguredLimitIsAllowedButOverflowIsRejectedBeforeWritingRows() {
        List<AuditExportRow> exactRows = new ArrayList<>();
        AuditExportQuery query = exportQuery(
                null,
                Instant.parse("2026-09-15T00:00:00Z"),
                Instant.parse("2026-09-16T23:59:59Z"),
                null,
                null);

        queryAdapter.streamExport(query, new AuditExportPolicy(Duration.ofDays(3), 3),
                AuditVisibilityScope.organization(),
                exactRows::add);
        assertThat(exactRows).hasSize(3);

        List<AuditExportRow> overflowRows = new ArrayList<>();
        assertThatThrownBy(() -> queryAdapter.streamExport(
                query,
                new AuditExportPolicy(Duration.ofDays(3), 2),
                AuditVisibilityScope.organization(),
                overflowRows::add))
                .isInstanceOf(AuditExportLimitExceededException.class)
                .hasMessage("The audit export exceeds the configured row limit.");
        assertThat(overflowRows).isEmpty();
    }

    @Test
    void emptyExportDoesNotInvokeTheSinkAndRangePolicyRunsBeforeSql() {
        List<AuditExportRow> rows = new ArrayList<>();

        queryAdapter.streamExport(
                exportQuery(
                        null,
                        Instant.parse("2026-09-17T00:00:00Z"),
                        Instant.parse("2026-09-17T23:59:59Z"),
                        null,
                        null),
                new AuditExportPolicy(Duration.ofDays(1), 3),
                AuditVisibilityScope.organization(),
                rows::add);
        assertThat(rows).isEmpty();

        assertThatThrownBy(() -> queryAdapter.streamExport(
                exportQuery(
                        null,
                        Instant.parse("2026-09-15T00:00:00Z"),
                        Instant.parse("2026-09-16T23:59:59Z"),
                        null,
                        null),
                new AuditExportPolicy(Duration.ofDays(1), 3),
                AuditVisibilityScope.organization(),
                rows::add))
                .isInstanceOf(AuditExportValidationException.class)
                .hasMessage("The audit export date range exceeds the configured maximum.");
    }

    @Test
    void closesTheStreamingResourcesWhenTheSinkAborts() {
        AuditExportQuery query = exportQuery(
                null,
                Instant.parse("2026-09-15T00:00:00Z"),
                Instant.parse("2026-09-16T23:59:59Z"),
                null,
                null);

        assertThatThrownBy(() -> queryAdapter.streamExport(
                query,
                new AuditExportPolicy(Duration.ofDays(3), 3),
                AuditVisibilityScope.organization(),
                row -> {
                    throw new IllegalStateException("client disconnected");
                }))
                .isInstanceOf(AuditExportDataAccessException.class)
                .hasMessage("Audit export data could not be read.");

        assertThat(queryAdapter.findAll(
                AuditSearchQuery.defaults(), AuditVisibilityScope.organization()).items())
                .hasSize(3);
    }

    @Test
    void treatsFilterTextAsAParameterRatherThanExecutableSql() {
        List<AuditExportRow> rows = new ArrayList<>();

        queryAdapter.streamExport(
                AuditExportQuery.from(
                        null,
                        "audit-admin' or '1'='1",
                        null,
                        null,
                        null,
                        null,
                        null,
                        Instant.parse("2026-09-15T00:00:00Z"),
                        Instant.parse("2026-09-16T23:59:59Z"),
                        null,
                        null),
                new AuditExportPolicy(Duration.ofDays(3), 3),
                AuditVisibilityScope.organization(),
                rows::add);

        assertThat(rows).isEmpty();
    }

    private static AuditExportQuery exportQuery(
            UUID actorUserId,
            Instant occurredFrom,
            Instant occurredUntil,
            String direction,
            String actionCode) {
        return AuditExportQuery.from(
                actorUserId,
                actionCode == null ? null : "AUDIT-ADMIN",
                actionCode,
                actionCode == null ? null : "PAYMENT",
                actionCode == null ? null : PAYMENT_ID,
                actionCode == null ? null : "PAY-000002",
                actionCode == null ? null : CORRELATION_ID,
                occurredFrom,
                occurredUntil,
                null,
                direction);
    }

    private void insertAuditEntry(
            UUID id,
            String actorIdentifier,
            String actionCode,
            String resourceType,
            UUID resourceId,
            String resourceCode,
            String summary,
            String metadata,
            UUID correlationId,
            Instant occurredAt) {
        jdbcTemplate.update("""
                insert into gym.audit_entries
                    (id, actor_user_id, actor_identifier_snapshot, action_code,
                     resource_type, resource_id, resource_code_snapshot, summary,
                     metadata, correlation_id, occurred_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?)
                """,
                id,
                adminId,
                actorIdentifier,
                actionCode,
                resourceType,
                resourceId,
                resourceCode,
                summary,
                metadata,
                correlationId,
                OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC));
    }
}
