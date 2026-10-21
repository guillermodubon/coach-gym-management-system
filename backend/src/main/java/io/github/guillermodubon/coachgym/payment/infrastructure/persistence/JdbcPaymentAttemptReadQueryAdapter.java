package io.github.guillermodubon.coachgym.payment.infrastructure.persistence;

import io.github.guillermodubon.coachgym.payment.PaymentAttemptDetails;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptFailureCode;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptStatus;
import io.github.guillermodubon.coachgym.payment.PaymentProvider;
import io.github.guillermodubon.coachgym.payment.application.PaymentAttemptPage;
import io.github.guillermodubon.coachgym.payment.application.PaymentAttemptQueryUnavailableException;
import io.github.guillermodubon.coachgym.payment.application.PaymentAttemptReadQuery;
import io.github.guillermodubon.coachgym.payment.application.PaymentAttemptSearchQuery;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Bounded PostgreSQL read model for payment-attempt recovery. */
@Repository
class JdbcPaymentAttemptReadQueryAdapter implements PaymentAttemptReadQuery {

    private static final String FROM_WHERE = """
            from gym.payment_attempts a
            where a.membership_id = :membershipId
              and a.membership_period_id = :membershipPeriodId
              and a.initiated_at_branch_id = :branchId
            """;

    private static final String SAFE_COLUMNS = """
            a.id, a.client_id, a.membership_id, a.membership_period_id,
            a.provider, a.status, a.expected_amount, a.currency, a.failure_code,
            a.confirmed_payment_id, a.created_by_user_id, a.created_at,
            a.updated_at, a.completed_at, a.version, a.initiated_at_branch_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcPaymentAttemptReadQueryAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentAttemptPage findPage(
            PaymentAttemptSearchQuery query,
            UUID authorizedBranchId) {
        Objects.requireNonNull(query, "Payment-attempt query is required.");
        Objects.requireNonNull(authorizedBranchId, "Authorized branch is required.");
        QueryFilters filters = filters(query, authorizedBranchId);
        try {
            Long count = jdbcTemplate.queryForObject(
                    "select count(*) " + FROM_WHERE + filters.whereClause(),
                    filters.parameters(),
                    Long.class);
            long totalElements = count == null ? 0 : count;
            List<PaymentAttemptDetails> items = jdbcTemplate.query(
                    "select " + SAFE_COLUMNS + FROM_WHERE + filters.whereClause()
                            + " order by a.created_at desc, a.id asc"
                            + " limit :pageSize offset :pageOffset",
                    filters.parameters()
                            .addValue("pageSize", query.size())
                            .addValue("pageOffset", query.offset()),
                    JdbcPaymentAttemptReadQueryAdapter::mapAttempt);
            return new PaymentAttemptPage(
                    items, query.page(), query.size(), totalElements,
                    totalPages(totalElements, query.size()));
        } catch (DataAccessException exception) {
            throw new PaymentAttemptQueryUnavailableException(exception);
        }
    }

    private static QueryFilters filters(
            PaymentAttemptSearchQuery query,
            UUID authorizedBranchId) {
        StringBuilder where = new StringBuilder();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("membershipId", query.membershipId())
                .addValue("membershipPeriodId", query.membershipPeriodId())
                .addValue("branchId", authorizedBranchId);
        if (query.clientId() != null) {
            where.append(" and a.client_id = :clientId");
            parameters.addValue("clientId", query.clientId());
        }
        if (query.status() != null) {
            where.append(" and a.status = :status");
            parameters.addValue("status", query.status().name());
        }
        return new QueryFilters(where.toString(), parameters);
    }

    private static PaymentAttemptDetails mapAttempt(ResultSet resultSet, int rowNumber)
            throws SQLException {
        String failureCode = resultSet.getString("failure_code");
        return new PaymentAttemptDetails(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("client_id", UUID.class),
                resultSet.getObject("membership_id", UUID.class),
                resultSet.getObject("membership_period_id", UUID.class),
                PaymentProvider.valueOf(resultSet.getString("provider")),
                PaymentAttemptStatus.valueOf(resultSet.getString("status")),
                resultSet.getBigDecimal("expected_amount"),
                resultSet.getString("currency").trim(),
                failureCode == null ? null : PaymentAttemptFailureCode.valueOf(failureCode),
                resultSet.getObject("confirmed_payment_id", UUID.class),
                resultSet.getObject("created_by_user_id", UUID.class),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"),
                nullableInstant(resultSet, "completed_at"),
                resultSet.getLong("version"),
                resultSet.getObject("initiated_at_branch_id", UUID.class));
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static Instant nullableInstant(ResultSet resultSet, String column)
            throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static int totalPages(long totalElements, int size) {
        long pages = totalElements / size + (totalElements % size == 0 ? 0 : 1);
        return pages > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) pages;
    }

    private record QueryFilters(String whereClause, MapSqlParameterSource parameters) {
    }
}
