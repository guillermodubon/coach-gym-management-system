package io.github.guillermodubon.coachgym.payment.infrastructure.persistence;

import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import io.github.guillermodubon.coachgym.payment.PaymentStatusHistoryDetails;
import io.github.guillermodubon.coachgym.payment.PaymentStatusHistoryPage;
import io.github.guillermodubon.coachgym.payment.application.PaymentCorrectionDataAccessException;
import io.github.guillermodubon.coachgym.payment.application.PaymentStatusHistoryQuery;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcPaymentStatusHistoryQuery implements PaymentStatusHistoryQuery {

    static final String SELECT_SQL = """
            select id, payment_id, previous_status, new_status,
                   reason, occurred_at, changed_by_user_id
            from gym.payment_status_history
            where payment_id = :paymentId
              and previous_status is not null
            order by occurred_at desc, id desc
            limit :limit offset :offset
            """;

    static final String COUNT_SQL = """
            select count(*)
            from gym.payment_status_history
            where payment_id = :paymentId
              and previous_status is not null
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcPaymentStatusHistoryQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentStatusHistoryPage findByPaymentId(
            UUID paymentId,
            int page,
            int size) {
        Objects.requireNonNull(paymentId, "Payment id is required.");
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Payment history pagination is invalid.");
        }
        try {
            MapSqlParameterSource base = new MapSqlParameterSource()
                    .addValue("paymentId", paymentId);
            Long total = jdbcTemplate.queryForObject(
                    COUNT_SQL, base, Long.class);
            long totalElements = total == null ? 0 : total;
            List<PaymentStatusHistoryDetails> content = jdbcTemplate.query(
                    SELECT_SQL,
                    new MapSqlParameterSource()
                            .addValue("paymentId", paymentId)
                            .addValue("limit", size)
                            .addValue("offset", (long) page * size),
                    JdbcPaymentStatusHistoryQuery::mapHistory);
            int totalPages = totalElements == 0
                    ? 0
                    : (int) ((totalElements + size - 1) / size);
            return new PaymentStatusHistoryPage(
                    content, page, size, totalElements, totalPages);
        } catch (DataAccessException exception) {
            throw new PaymentCorrectionDataAccessException(
                    "Payment status history could not be read.", exception);
        }
    }

    private static PaymentStatusHistoryDetails mapHistory(
            ResultSet rs,
            int row) throws SQLException {
        return new PaymentStatusHistoryDetails(
                rs.getObject("id", UUID.class),
                rs.getObject("payment_id", UUID.class),
                PaymentStatus.valueOf(rs.getString("previous_status")),
                PaymentStatus.valueOf(rs.getString("new_status")),
                rs.getString("reason"),
                rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                rs.getObject("changed_by_user_id", UUID.class));
    }
}
