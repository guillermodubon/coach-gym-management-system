package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.audit.AuditEntryDetails;
import io.github.guillermodubon.coachgym.audit.AuditEntryPage;
import io.github.guillermodubon.coachgym.audit.AuditEntrySummary;
import io.github.guillermodubon.coachgym.audit.AuditExportDataAccessException;
import io.github.guillermodubon.coachgym.audit.AuditExportLimitExceededException;
import io.github.guillermodubon.coachgym.audit.AuditExportPolicy;
import io.github.guillermodubon.coachgym.audit.AuditExportQuery;
import io.github.guillermodubon.coachgym.audit.AuditSearchQuery;
import io.github.guillermodubon.coachgym.audit.application.AuditQueryDataAccessException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class JdbcAuditEntryQueryAdapterTest {

    private static final UUID ENTRY_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000a01");
    private static final UUID RESOURCE_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000a02");
    private static final UUID ACTOR_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000a03");

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private JdbcTemplate streamJdbcTemplate;

    @Test
    void listsBoundedSummariesWithCountAndDeterministicNewestOrder() {
        when(jdbcTemplate.queryForObject(
                eq(JdbcAuditEntryQueryAdapter.COUNT_SQL),
                any(MapSqlParameterSource.class),
                eq(Long.class)))
                .thenReturn(1L);
        when(jdbcTemplate.query(
                eq(JdbcAuditEntryQueryAdapter.PAGE_SQL_DESC),
                any(MapSqlParameterSource.class),
                any(RowMapper.class)))
                .thenReturn(List.of(summary()));

        AuditEntryPage page = adapter().findAll(AuditSearchQuery.defaults());

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().id()).isEqualTo(ENTRY_ID);
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.totalPages()).isEqualTo(1);
        verify(jdbcTemplate, times(1)).queryForObject(
                eq(JdbcAuditEntryQueryAdapter.COUNT_SQL),
                any(MapSqlParameterSource.class),
                eq(Long.class));
        verify(jdbcTemplate, times(1)).query(
                eq(JdbcAuditEntryQueryAdapter.PAGE_SQL_DESC),
                any(MapSqlParameterSource.class),
                any(RowMapper.class));
    }

    @Test
    void mapsDetailMetadataThroughTheSanitizingProjector() throws Exception {
        ResultSet resultSet = detailResultSet();
        when(jdbcTemplate.query(
                eq(JdbcAuditEntryQueryAdapter.DETAIL_SQL),
                any(MapSqlParameterSource.class),
                any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<AuditEntryDetails> mapper = invocation.getArgument(2);
                    return List.of(mapper.mapRow(resultSet, 0));
                });

        Optional<AuditEntryDetails> details = adapter().findById(ENTRY_ID);

        assertThat(details).isPresent();
        assertThat(details.orElseThrow().metadata().values())
                .containsEntry("result", "ALLOWED")
                .doesNotContainKey("token")
                .doesNotContainKey("raw_payload");
        assertThat(details.orElseThrow().metadataRedacted()).isTrue();
    }

    @Test
    void absentDetailIsNotADataAccessFailure() {
        when(jdbcTemplate.query(
                eq(JdbcAuditEntryQueryAdapter.DETAIL_SQL),
                any(MapSqlParameterSource.class),
                any(RowMapper.class)))
                .thenReturn(List.of());

        assertThat(adapter().findById(ENTRY_ID)).isEmpty();
    }

    @Test
    void translatesDatabaseFailuresWithoutExposingDriverDetails() {
        when(jdbcTemplate.queryForObject(
                eq(JdbcAuditEntryQueryAdapter.COUNT_SQL),
                any(MapSqlParameterSource.class),
                eq(Long.class)))
                .thenThrow(new DataRetrievalFailureException("password=secret"));

        assertThatThrownBy(() -> adapter().findAll(AuditSearchQuery.defaults()))
                .isInstanceOf(AuditQueryDataAccessException.class)
                .hasMessage("Audit entries could not be read.");
    }

    @Test
    void nullDetailIdIsRejectedBeforeDatabaseAccess() {
        assertThatThrownBy(() -> adapter().findById(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Audit entry id must be provided.");

        verify(jdbcTemplate, never()).query(
                any(String.class),
                any(MapSqlParameterSource.class),
                any(RowMapper.class));
    }

    @Test
    void sqlUsesFixedPredicatesAndOnlyEnumBackedSortVariants() {
        assertThat(JdbcAuditEntryQueryAdapter.WHERE_SQL)
                .contains(":actorUserId", ":actionCode", ":resourceId",
                        ":correlationId", ":occurredFrom", ":occurredUntil")
                .doesNotContain("?1", "${", "rawSql");
        assertThat(JdbcAuditEntryQueryAdapter.PAGE_SQL_DESC)
                .contains("order by occurred_at desc, id desc")
                .doesNotContain("metadata");
        assertThat(JdbcAuditEntryQueryAdapter.PAGE_SQL_ASC)
                .contains("order by occurred_at asc, id asc")
                .doesNotContain("metadata");
        assertThat(JdbcAuditEntryQueryAdapter.EXPORT_SQL_DESC)
                .contains("metadata::text as metadata_json")
                .contains("order by occurred_at desc, id asc")
                .contains("limit ?");
        assertThat(JdbcAuditEntryQueryAdapter.EXPORT_SQL_ASC)
                .contains("order by occurred_at asc, id asc")
                .contains("limit ?");
        assertThat(JdbcAuditEntryQueryAdapter.EXPORT_COUNT_SQL)
                .contains("cast(? as uuid)")
                .doesNotContain(":actorUserId");
    }

    @Test
    void rejectsOverflowBeforeOpeningTheStreamingSelect() {
        when(streamJdbcTemplate.query(
                any(PreparedStatementCreator.class),
                any(PreparedStatementSetter.class),
                any(ResultSetExtractor.class)))
                .thenReturn(3L);

        assertThatThrownBy(() -> adapter().streamExport(
                exportQuery(),
                new AuditExportPolicy(Duration.ofDays(1), 2),
                row -> { }))
                .isInstanceOf(AuditExportLimitExceededException.class);

        verify(streamJdbcTemplate, times(1)).query(
                any(PreparedStatementCreator.class),
                any(PreparedStatementSetter.class),
                any(ResultSetExtractor.class));
    }

    @Test
    void translatesExportDatabaseFailuresWithoutExposingDriverDetails() {
        when(streamJdbcTemplate.query(
                any(PreparedStatementCreator.class),
                any(PreparedStatementSetter.class),
                any(ResultSetExtractor.class)))
                .thenThrow(new DataRetrievalFailureException("password=secret"));

        assertThatThrownBy(() -> adapter().streamExport(
                exportQuery(),
                new AuditExportPolicy(Duration.ofDays(1), 2),
                row -> { }))
                .isInstanceOf(AuditExportDataAccessException.class)
                .hasMessage("Audit export data could not be read.")
                .hasMessageNotContaining("password");
    }

    @Test
    void configuresForwardOnlyFetchSizeForTheStreamingStatement() throws Exception {
        when(streamJdbcTemplate.query(
                any(PreparedStatementCreator.class),
                any(PreparedStatementSetter.class),
                any(ResultSetExtractor.class)))
                .thenReturn(0L)
                .thenReturn(null);

        adapter().streamExport(
                exportQuery(),
                new AuditExportPolicy(Duration.ofDays(1), 2),
                row -> { });

        var captor = org.mockito.ArgumentCaptor.forClass(
                PreparedStatementCreator.class);
        verify(streamJdbcTemplate, times(2)).query(
                captor.capture(),
                any(PreparedStatementSetter.class),
                any(ResultSetExtractor.class));

        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(
                JdbcAuditEntryQueryAdapter.EXPORT_SQL_DESC,
                ResultSet.TYPE_FORWARD_ONLY,
                ResultSet.CONCUR_READ_ONLY))
                .thenReturn(statement);
        captor.getAllValues().get(1).createPreparedStatement(connection);

        verify(statement).setFetchSize(JdbcAuditEntryQueryAdapter.EXPORT_FETCH_SIZE);
    }

    @Test
    void exportReadPathIsDeclaredReadOnly() throws Exception {
        assertThat(JdbcAuditEntryQueryAdapter.class
                .getMethod(
                        "streamExport",
                        io.github.guillermodubon.coachgym.audit.AuditExportQuery.class,
                        AuditExportPolicy.class,
                        io.github.guillermodubon.coachgym.audit.AuditExportSink.class)
                .getAnnotation(Transactional.class)
                .readOnly())
                .isTrue();
    }

    private JdbcAuditEntryQueryAdapter adapter() {
        return new JdbcAuditEntryQueryAdapter(
                jdbcTemplate,
                streamJdbcTemplate,
                new tools.jackson.databind.json.JsonMapper(),
                new io.github.guillermodubon.coachgym.audit.application.AuditEntryProjector());
    }

    private static AuditExportQuery exportQuery() {
        return AuditExportQuery.from(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-09-16T00:00:00Z"),
                Instant.parse("2026-09-16T23:59:59Z"),
                null,
                null);
    }

    private static AuditEntrySummary summary() {
        return new AuditEntrySummary(
                ENTRY_ID,
                "ACCESS_DENIED",
                "ACCESS_RECORD",
                RESOURCE_ID,
                "ACCESS-000001",
                ACTOR_ID,
                "audit-admin",
                "Gym access allowed.",
                java.time.Instant.parse("2026-09-16T12:00:00Z"),
                null);
    }

    private static ResultSet detailResultSet() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        OffsetDateTime occurredAt = OffsetDateTime.of(
                2026, 9, 16, 12, 0, 0, 123_000_000, ZoneOffset.UTC);
        when(resultSet.getObject("id", UUID.class)).thenReturn(ENTRY_ID);
        when(resultSet.getObject("actor_user_id", UUID.class)).thenReturn(ACTOR_ID);
        when(resultSet.getString("actor_identifier_snapshot"))
                .thenReturn("audit-admin");
        when(resultSet.getString("action_code")).thenReturn("ACCESS_DENIED");
        when(resultSet.getString("resource_type")).thenReturn("ACCESS_RECORD");
        when(resultSet.getObject("resource_id", UUID.class)).thenReturn(RESOURCE_ID);
        when(resultSet.getString("resource_code_snapshot"))
                .thenReturn("ACCESS-000001");
        when(resultSet.getString("summary"))
                .thenReturn("Gym access allowed.");
        when(resultSet.getString("metadata_json"))
                .thenReturn("{\"result\":\"ALLOWED\",\"token\":\"secret\","
                        + "\"raw_payload\":\"data:application/octet-stream;base64,AAAA\"}");
        when(resultSet.getObject("correlation_id", UUID.class)).thenReturn(null);
        when(resultSet.getObject("occurred_at", OffsetDateTime.class))
                .thenReturn(occurredAt);
        return resultSet;
    }
}
