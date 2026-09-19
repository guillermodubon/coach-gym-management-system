package io.github.guillermodubon.coachgym.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditExportPublicContractTest {

    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final UUID RESOURCE_ID = UUID.randomUUID();
    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void requiresBothInclusiveBoundsAndRejectsAnInvertedRange() {
        assertThatThrownBy(() -> exportQuery(null, FROM))
                .isInstanceOf(AuditExportValidationException.class)
                .extracting(exception -> ((AuditExportValidationException) exception).code())
                .isEqualTo(AuditExportValidationException.RANGE_REQUIRED_CODE);
        assertThatThrownBy(() -> exportQuery(FROM, null))
                .isInstanceOf(AuditExportValidationException.class)
                .extracting(exception -> ((AuditExportValidationException) exception).code())
                .isEqualTo(AuditExportValidationException.RANGE_REQUIRED_CODE);
        assertThatThrownBy(() -> exportQuery(FROM.plusSeconds(1), FROM))
                .isInstanceOf(AuditExportValidationException.class)
                .hasMessageContaining("occurredFrom");
    }

    @Test
    void enforcesConfiguredSpanAndRowBoundariesWithoutSilentTruncation() {
        AuditExportPolicy defaults = AuditExportPolicy.defaults();
        defaults.validateRange(FROM, FROM.plus(AuditExportPolicy.DEFAULT_MAX_DATE_RANGE));
        assertThatThrownBy(() -> defaults.validateRange(
                FROM, FROM.plus(AuditExportPolicy.DEFAULT_MAX_DATE_RANGE).plusSeconds(1)))
                .isInstanceOf(AuditExportValidationException.class)
                .extracting(exception -> ((AuditExportValidationException) exception).code())
                .isEqualTo(AuditExportValidationException.RANGE_TOO_LARGE_CODE);

        AuditExportPolicy configured = new AuditExportPolicy(Duration.ofDays(90), 20);
        configured.validateRange(FROM, FROM.plus(Duration.ofDays(90)));
        configured.validateRowCount(20);
        assertThatThrownBy(() -> configured.validateRowCount(21))
                .isInstanceOf(AuditExportLimitExceededException.class)
                .extracting(exception -> ((AuditExportLimitExceededException) exception).code())
                .isEqualTo(AuditExportLimitExceededException.CODE);
        assertThatThrownBy(() -> new AuditExportPolicy(
                AuditExportPolicy.HARD_MAX_DATE_RANGE.plusNanos(1), 20))
                .isInstanceOf(AuditExportValidationException.class);
        assertThatThrownBy(() -> new AuditExportPolicy(Duration.ofDays(1),
                AuditExportPolicy.HARD_MAX_ROWS + 1))
                .isInstanceOf(AuditExportValidationException.class);
    }

    @Test
    void reusesAuditFilterAndSortAllowlistsAndDoesNotExposePagination() {
        AuditExportQuery query = AuditExportQuery.from(
                ACTOR_ID,
                "  Admin.User ",
                " client_registered ",
                " client ",
                RESOURCE_ID,
                " CLI-001 ",
                null,
                FROM,
                FROM.plusSeconds(10),
                null,
                null);

        assertThat(query.actorIdentifier()).isEqualTo("admin.user");
        assertThat(query.actionCode()).isEqualTo("CLIENT_REGISTERED");
        assertThat(query.resourceType()).isEqualTo("CLIENT");
        assertThat(query.resourceCode()).isEqualTo("CLI-001");
        assertThat(query.sort()).isEqualTo(AuditSortField.OCCURRED_AT);
        assertThat(query.sortDirection()).isEqualTo(AuditSortDirection.DESC);
        assertThat(query.getClass().getRecordComponents())
                .extracting(RecordComponent::getName)
                .doesNotContain("page", "size", "metadata", "column", "result");

        assertThatThrownBy(() -> AuditExportQuery.from(
                null, null, "UNKNOWN_ACTION", null, null, null, null,
                FROM, FROM, null, null))
                .isInstanceOf(AuditQueryValidationException.class);
    }

    @Test
    void exposesOnlyFixedSafeColumnsInStableOrder() {
        assertThat(AuditExportColumn.headers()).containsExactly(
                "entry_id",
                "occurred_at",
                "actor_user_id",
                "actor_identifier",
                "action_code",
                "resource_type",
                "resource_id",
                "resource_code",
                "summary",
                "correlation_id",
                "metadata");
        assertThatThrownBy(() -> AuditExportColumn.ordered().add(AuditExportColumn.ENTRY_ID))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rowAcceptsOnlyTheSanitizedMetadataProjection() {
        AuditMetadataProjection metadata = new AuditMetadataProjection(
                Map.of("status", "ACTIVE"), true);
        AuditExportRow row = new AuditExportRow(
                UUID.randomUUID(),
                FROM,
                ACTOR_ID,
                "admin.user",
                "CLIENT_REGISTERED",
                "CLIENT",
                RESOURCE_ID,
                "CLI-001",
                "Client registered.",
                null,
                metadata);

        assertThat(row.metadata()).isSameAs(metadata);
        assertThatThrownBy(() -> new AuditMetadataProjection(
                Map.of("stripe_secret_key", "hidden"), false))
                .isInstanceOf(AuditQueryValidationException.class);
        assertThatThrownBy(() -> new AuditExportResult(0, null))
                .isInstanceOf(AuditExportValidationException.class);
    }

    @Test
    void policyAndContractsRemainAdministrativeAndTechnologyNeutral() throws Exception {
        assertThat(AuditExportPolicy.ADMIN_ONLY).isTrue();
        assertThat(AuditExportPolicy.UTF_8_BOM).isFalse();
        assertThat(AuditExportPolicy.OVERFLOW_REJECTED).isTrue();
        assertThat(AuditExportPolicy.defaults().maxRows())
                .isEqualTo(AuditExportPolicy.DEFAULT_MAX_ROWS);

        List<Class<?>> contracts = List.of(
                AuditExportActor.class,
                AuditExportCompleted.class,
                AuditExportQuery.class,
                AuditExportRow.class,
                AuditExportResult.class,
                AuditExportPolicy.class,
                AuditExportColumn.class,
                AuditExportSink.class,
                AuditExportValidationException.class,
                AuditExportLimitExceededException.class,
                AuditExportDataAccessException.class,
                AuditExportAuditException.class,
                AuditExportStreamException.class);
        for (Class<?> contract : contracts) {
            for (RecordComponent component : contract.isRecord()
                    ? contract.getRecordComponents() : new RecordComponent[0]) {
                assertSafeType(component.getType());
            }
            for (var method : contract.getDeclaredMethods()) {
                assertSafeType(method.getReturnType());
                for (Class<?> parameter : method.getParameterTypes()) {
                    assertSafeType(parameter);
                }
            }
            assertThat(contract.getName())
                    .doesNotContain(".infrastructure", ".web", "org.springframework",
                            "java.sql", "jakarta.persistence", "com.fasterxml.jackson");
        }
    }

    private static AuditExportQuery exportQuery(Instant from, Instant until) {
        return new AuditExportQuery(
                null, null, null, null, null, null, null, from, until, null, null);
    }

    private static void assertSafeType(Class<?> type) {
        assertThat(type.getName())
                .doesNotContain("jakarta.persistence", "org.springframework",
                        "org.hibernate", "java.sql", ".infrastructure",
                        "com.fasterxml.jackson");
    }
}
