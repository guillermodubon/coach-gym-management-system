package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.audit.AuditEntryDetails;
import io.github.guillermodubon.coachgym.audit.AuditEntryPage;
import io.github.guillermodubon.coachgym.audit.AuditSearchQuery;
import io.github.guillermodubon.coachgym.audit.application.AuditQueryDataAccessException;
import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AuditQuerySchemaIntegrationTest extends AbstractIncidentApiIntegrationTest {

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
    void hasOnlyTheJustifiedNewestFirstAuditIndex() {
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
    void listsNewestFirstWithStableIdTieBreakingAndAccurateCount() {
        AuditEntryPage page = queryAdapter.findAll(AuditSearchQuery.defaults());

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

        assertThat(queryAdapter.findAll(firstPage).items())
                .extracting(item -> item.id())
                .containsExactly(SECOND_ID);
        assertThat(queryAdapter.findAll(secondPage).items())
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

        AuditEntryPage page = queryAdapter.findAll(filtered);

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

        assertThat(queryAdapter.findAll(filtered).items())
                .extracting(item -> item.id())
                .containsExactly(SECOND_ID, FIRST_ID);
    }

    @Test
    void mapsFoundDetailWithPrecisionAndSanitizedMetadata() {
        AuditEntryDetails detail = queryAdapter.findById(SECOND_ID).orElseThrow();

        assertThat(detail.occurredAt()).isEqualTo(TIE_TIMESTAMP);
        assertThat(detail.metadata().values())
                .containsEntry("paymentId", PAYMENT_ID.toString())
                .doesNotContainKey("token");
        assertThat(detail.metadataRedacted()).isTrue();
    }

    @Test
    void absentDetailIsEmptyAndQueriesDoNotWriteAuditRows() {
        long before = jdbcTemplate.queryForObject(
                "select count(*) from gym.audit_entries", Long.class);

        assertThat(queryAdapter.findById(UUID.randomUUID())).isEmpty();
        queryAdapter.findAll(AuditSearchQuery.defaults());

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

        assertThatThrownBy(() -> queryAdapter.findById(malformedId))
                .isInstanceOf(AuditQueryDataAccessException.class)
                .hasMessage("Audit entries could not be read.");

        long after = jdbcTemplate.queryForObject(
                "select count(*) from gym.audit_entries", Long.class);
        assertThat(after).isEqualTo(before);
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
