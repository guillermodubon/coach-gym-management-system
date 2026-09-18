package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import io.github.guillermodubon.coachgym.audit.AuditEntryDetails;
import io.github.guillermodubon.coachgym.audit.AuditEntryPage;
import io.github.guillermodubon.coachgym.audit.AuditEntryQuery;
import io.github.guillermodubon.coachgym.audit.AuditEntrySummary;
import io.github.guillermodubon.coachgym.audit.AuditExportDataAccessException;
import io.github.guillermodubon.coachgym.audit.AuditExportPolicy;
import io.github.guillermodubon.coachgym.audit.AuditExportQuery;
import io.github.guillermodubon.coachgym.audit.AuditExportRow;
import io.github.guillermodubon.coachgym.audit.AuditExportSink;
import io.github.guillermodubon.coachgym.audit.AuditExportStreamException;
import io.github.guillermodubon.coachgym.audit.AuditExportValidationException;
import io.github.guillermodubon.coachgym.audit.AuditQueryValidationException;
import io.github.guillermodubon.coachgym.audit.AuditSearchQuery;
import io.github.guillermodubon.coachgym.audit.AuditSortDirection;
import io.github.guillermodubon.coachgym.audit.application.AuditEntryProjector;
import io.github.guillermodubon.coachgym.audit.application.AuditEntryRow;
import io.github.guillermodubon.coachgym.audit.application.AuditQueryDataAccessException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * PostgreSQL read adapter for immutable audit history.
 *
 * <p>All predicates are fixed SQL with named parameters. The only SQL
 * variation is selected from the enum-backed sort allowlist, and the list
 * query deliberately omits JSONB metadata.</p>
 */
@Repository
class JdbcAuditEntryQueryAdapter implements AuditEntryQuery {

    static final int EXPORT_FETCH_SIZE = 256;

    static final String WHERE_SQL = """
            where (cast(:actorUserId as uuid) is null
                   or actor_user_id = cast(:actorUserId as uuid))
              and (cast(:actorIdentifier as varchar) is null
                   or lower(actor_identifier_snapshot) = cast(:actorIdentifier as varchar))
              and (cast(:actionCode as varchar) is null
                   or action_code = cast(:actionCode as varchar))
              and (cast(:resourceType as varchar) is null
                   or resource_type = cast(:resourceType as varchar))
              and (cast(:resourceId as uuid) is null
                   or resource_id = cast(:resourceId as uuid))
              and (cast(:resourceCode as varchar) is null
                   or resource_code_snapshot = cast(:resourceCode as varchar))
              and (cast(:correlationId as uuid) is null
                   or correlation_id = cast(:correlationId as uuid))
              and (cast(:occurredFrom as timestamptz) is null
                   or occurred_at >= cast(:occurredFrom as timestamptz))
              and (cast(:occurredUntil as timestamptz) is null
                   or occurred_at <= cast(:occurredUntil as timestamptz))
            """;

    static final String COUNT_SQL = """
            select count(*)
            from gym.audit_entries
            """ + WHERE_SQL;

    static final String PAGE_SELECT_SQL = """
            select id, actor_user_id, actor_identifier_snapshot,
                   action_code, resource_type, resource_id,
                   resource_code_snapshot, summary, correlation_id, occurred_at
            from gym.audit_entries
            """ + WHERE_SQL;

    static final String PAGE_SQL_DESC = PAGE_SELECT_SQL + """
            order by occurred_at desc, id desc
            limit :limit offset :offset
            """;

    static final String PAGE_SQL_ASC = PAGE_SELECT_SQL + """
            order by occurred_at asc, id asc
            limit :limit offset :offset
            """;

    private static final String EXPORT_WHERE_SQL = positionalWhereSql();

    static final String EXPORT_COUNT_SQL = """
            select count(*)
            from gym.audit_entries
            """ + EXPORT_WHERE_SQL;

    private static final String EXPORT_SELECT_SQL = """
            select id, actor_user_id, actor_identifier_snapshot,
                   action_code, resource_type, resource_id,
                   resource_code_snapshot, summary,
                   metadata::text as metadata_json, correlation_id, occurred_at
            from gym.audit_entries
            """ + EXPORT_WHERE_SQL;

    static final String EXPORT_SQL_DESC = EXPORT_SELECT_SQL + """
            order by occurred_at desc, id asc
            limit ?
            """;

    static final String EXPORT_SQL_ASC = EXPORT_SELECT_SQL + """
            order by occurred_at asc, id asc
            limit ?
            """;

    static final String DETAIL_SQL = """
            select id, actor_user_id, actor_identifier_snapshot,
                   action_code, resource_type, resource_id,
                   resource_code_snapshot, summary, metadata::text as metadata_json,
                   correlation_id, occurred_at
            from gym.audit_entries
            where id = cast(:auditEntryId as uuid)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final JdbcTemplate streamJdbcTemplate;
    private final JsonMapper jsonMapper;
    private final AuditEntryProjector projector;

    @Autowired
    JdbcAuditEntryQueryAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, jdbcTemplate.getJdbcTemplate(),
                new JsonMapper(), new AuditEntryProjector());
    }

    JdbcAuditEntryQueryAdapter(
            NamedParameterJdbcTemplate jdbcTemplate,
            JdbcTemplate streamJdbcTemplate,
            JsonMapper jsonMapper,
            AuditEntryProjector projector) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
        this.streamJdbcTemplate = Objects.requireNonNull(streamJdbcTemplate);
        this.jsonMapper = Objects.requireNonNull(jsonMapper);
        this.projector = Objects.requireNonNull(projector);
    }

    JdbcAuditEntryQueryAdapter(
            NamedParameterJdbcTemplate jdbcTemplate,
            JsonMapper jsonMapper,
            AuditEntryProjector projector) {
        this(jdbcTemplate, jdbcTemplate.getJdbcTemplate(), jsonMapper, projector);
    }

    @Override
    @Transactional(readOnly = true)
    public AuditEntryPage findAll(AuditSearchQuery query) {
        AuditSearchQuery validated = requireQuery(query);
        MapSqlParameterSource parameters = parameters(validated);
        try {
            Long totalElements = jdbcTemplate.queryForObject(
                    COUNT_SQL, parameters, Long.class);
            if (totalElements == null) {
                throw new AuditQueryDataAccessException(
                        "Audit entries could not be read.", null);
            }
            List<AuditEntrySummary> summaries = jdbcTemplate.query(
                    pageSql(validated), parameters, (resultSet, rowNumber) ->
                            projector.toSummary(mapRow(resultSet, false)));
            return AuditEntryPage.of(
                    summaries,
                    validated.page(),
                    validated.size(),
                    totalElements);
        } catch (AuditQueryDataAccessException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw dataAccess(exception);
        } catch (RuntimeException exception) {
            throw dataAccess(exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuditEntryDetails> findById(UUID auditEntryId) {
        if (auditEntryId == null) {
            throw new AuditQueryValidationException(
                    "Audit entry id must be provided.");
        }
        try {
            List<AuditEntryDetails> details = jdbcTemplate.query(
                    DETAIL_SQL,
                    new MapSqlParameterSource()
                            .addValue("auditEntryId", auditEntryId),
                    (resultSet, rowNumber) ->
                            projector.toDetails(mapRow(resultSet, true)));
            return details.stream().findFirst();
        } catch (DataAccessException exception) {
            throw dataAccess(exception);
        } catch (RuntimeException exception) {
            throw dataAccess(exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void streamExport(
            AuditExportQuery query,
            AuditExportPolicy policy,
            AuditExportSink sink) {
        if (query == null) {
            throw new AuditExportValidationException(
                    "Audit export query must be provided.");
        }
        if (sink == null) {
            throw new AuditExportValidationException(
                    "Audit export sink must be provided.");
        }
        query.validate(policy);

        long totalRows;
        try {
            totalRows = countExportRows(query);
        } catch (DataAccessException exception) {
            throw exportDataAccess(exception);
        }
        policy.validateRowCount(totalRows);

        try {
            streamJdbcTemplate.query(
                    preparedStatement(exportSql(query), EXPORT_FETCH_SIZE),
                    setter(exportParameters(query, policy.maxRows())),
                    (resultSet) -> {
                        while (resultSet.next()) {
                            sink.accept(new AuditExportRow(
                                    projector.toDetails(mapRow(resultSet, true))));
                        }
                        return null;
                    });
        } catch (DataAccessException exception) {
            throw exportDataAccess(exception);
        } catch (AuditExportStreamException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw exportDataAccess(exception);
        }
    }

    static String pageSql(AuditSearchQuery query) {
        return query.direction() == AuditSortDirection.ASC
                ? PAGE_SQL_ASC
                : PAGE_SQL_DESC;
    }

    static String exportSql(AuditExportQuery query) {
        return query.direction() == AuditSortDirection.ASC
                ? EXPORT_SQL_ASC
                : EXPORT_SQL_DESC;
    }

    private long countExportRows(AuditExportQuery query) {
        Long count = streamJdbcTemplate.query(
                preparedStatement(EXPORT_COUNT_SQL, 0),
                setter(exportParameters(query, null)),
                resultSet -> {
                    if (!resultSet.next()) {
                        return null;
                    }
                    return resultSet.getLong(1);
                });
        if (count == null) {
            throw new DataRetrievalFailureException(
                    "Audit export count returned no row.");
        }
        return count;
    }

    private static PreparedStatementCreator preparedStatement(
            String sql,
            int fetchSize) {
        return connection -> createStatement(connection, sql, fetchSize);
    }

    private static PreparedStatement createStatement(
            Connection connection,
            String sql,
            int fetchSize) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(
                sql,
                ResultSet.TYPE_FORWARD_ONLY,
                ResultSet.CONCUR_READ_ONLY);
        if (fetchSize > 0) {
            statement.setFetchSize(fetchSize);
        }
        return statement;
    }

    private static PreparedStatementSetter setter(Object[] values) {
        return statement -> {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
        };
    }

    private static Object[] exportParameters(
            AuditExportQuery query,
            Integer limit) {
        List<Object> values = new ArrayList<>(19);
        addTwice(values, query.actorUserId());
        addTwice(values, query.actorIdentifier());
        addTwice(values, query.actionCode());
        addTwice(values, query.resourceType());
        addTwice(values, query.resourceId());
        addTwice(values, query.resourceCode());
        addTwice(values, query.correlationId());
        addTwice(values, offset(query.occurredFrom()));
        addTwice(values, offset(query.occurredUntil()));
        if (limit != null) {
            values.add(limit);
        }
        return values.toArray();
    }

    private static void addTwice(List<Object> values, Object value) {
        values.add(value);
        values.add(value);
    }

    private static String positionalWhereSql() {
        String sql = WHERE_SQL;
        for (String parameter : List.of(
                "actorUserId", "actorIdentifier", "actionCode", "resourceType",
                "resourceId", "resourceCode", "correlationId", "occurredFrom",
                "occurredUntil")) {
            sql = sql.replace(":" + parameter, "?");
        }
        return sql;
    }

    private static AuditSearchQuery requireQuery(AuditSearchQuery query) {
        if (query == null) {
            throw new AuditQueryValidationException(
                    "Audit search query must be provided.");
        }
        return query;
    }

    private static MapSqlParameterSource parameters(AuditSearchQuery query) {
        long offset = Math.multiplyExact((long) query.page(), query.size());
        return new MapSqlParameterSource()
                .addValue("actorUserId", query.actorUserId())
                .addValue("actorIdentifier", query.actorIdentifier())
                .addValue("actionCode", query.actionCode())
                .addValue("resourceType", query.resourceType())
                .addValue("resourceId", query.resourceId())
                .addValue("resourceCode", query.resourceCode())
                .addValue("correlationId", query.correlationId())
                .addValue("occurredFrom", offset(query.occurredFrom()))
                .addValue("occurredUntil", offset(query.occurredUntil()))
                .addValue("limit", query.size())
                .addValue("offset", offset);
    }

    private static OffsetDateTime offset(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private AuditEntryRow mapRow(ResultSet resultSet, boolean includeMetadata)
            throws SQLException {
        Map<String, Object> metadata = includeMetadata
                ? readMetadata(resultSet.getString("metadata_json"))
                : Map.of();
        return new AuditEntryRow(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("actor_user_id", UUID.class),
                resultSet.getString("actor_identifier_snapshot"),
                resultSet.getString("action_code"),
                resultSet.getString("resource_type"),
                resultSet.getObject("resource_id", UUID.class),
                resultSet.getString("resource_code_snapshot"),
                resultSet.getString("summary"),
                metadata,
                resultSet.getObject("correlation_id", UUID.class),
                instant(resultSet, "occurred_at"));
    }

    private Map<String, Object> readMetadata(String metadataJson) throws SQLException {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        try {
            Object parsed = jsonMapper.readValue(metadataJson, Object.class);
            if (!(parsed instanceof Map<?, ?> parsedMap)) {
                throw new IllegalStateException("Audit metadata must be a JSON object.");
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : parsedMap.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalStateException("Audit metadata key is not textual.");
                }
                metadata.put(key, entry.getValue());
            }
            return metadata;
        } catch (Exception exception) {
            throw new SQLException("Audit metadata could not be mapped.", exception);
        }
    }

    private static Instant instant(ResultSet resultSet, String column)
            throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        if (value != null) {
            return value.toInstant();
        }
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static AuditQueryDataAccessException dataAccess(
            DataAccessException exception) {
        return new AuditQueryDataAccessException(
                "Audit entries could not be read.", exception);
    }

    private static AuditQueryDataAccessException dataAccess(
            RuntimeException exception) {
        return new AuditQueryDataAccessException(
                "Audit entries could not be read.", exception);
    }

    private static AuditExportDataAccessException exportDataAccess(
            DataAccessException exception) {
        return new AuditExportDataAccessException(exception);
    }

    private static AuditExportDataAccessException exportDataAccess(
            RuntimeException exception) {
        return new AuditExportDataAccessException(exception);
    }
}
