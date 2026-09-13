package io.github.guillermodubon.coachgym.payment.infrastructure.persistence;

import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptOrganization;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptSourceSnapshot;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptDataAccessException;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptOrganizationQuery;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptSnapshotQuery;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcPaymentReceiptSnapshotQuery
        implements PaymentReceiptSnapshotQuery, PaymentReceiptOrganizationQuery {

    static final String PAYMENT_SNAPSHOT_SQL = """
            select p.id, p.payment_code, p.status, p.amount, p.currency,
                   p.payment_method, p.paid_at,
                   c.client_code,
                   trim(c.first_name || ' ' || c.last_name) as client_display_name,
                   m.membership_code,
                   mp.period_number, mp.plan_name_snapshot,
                   mp.promotion_name_snapshot, mp.list_price,
                   mp.discount_amount, mp.starts_on, mp.effective_ends_on
            from gym.payments p
            join gym.clients c on c.id = p.client_id
            join gym.memberships m on m.id = p.membership_id
            join gym.membership_periods mp
              on mp.id = p.membership_period_id
             and mp.membership_id = p.membership_id
            where p.id = :paymentId
              and mp.final_price = p.amount
              and mp.currency = p.currency
            """;

    static final String ORGANIZATION_SQL = """
            select display_name, legal_name, email, phone, address, time_zone
            from gym.gym_settings
            where id = 1
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcPaymentReceiptSnapshotQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentReceiptSourceSnapshot> findByPaymentId(UUID paymentId) {
        requireIdentifier(paymentId, "Payment id");
        try {
            List<PaymentReceiptSourceSnapshot> rows = jdbcTemplate.query(
                    PAYMENT_SNAPSHOT_SQL,
                    new MapSqlParameterSource("paymentId", paymentId),
                    JdbcPaymentReceiptSnapshotQuery::mapSnapshot);
            return rows.stream().findFirst();
        } catch (DataAccessException exception) {
            throw new PaymentReceiptDataAccessException(
                    "Payment receipt source could not be read.", exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentReceiptOrganization findCurrent() {
        try {
            List<PaymentReceiptOrganization> rows = jdbcTemplate.query(
                    ORGANIZATION_SQL,
                    new MapSqlParameterSource(),
                    JdbcPaymentReceiptSnapshotQuery::mapOrganization);
            return rows.stream().findFirst().orElseThrow(() ->
                    new PaymentReceiptDataAccessException(
                            "Organization settings could not be read.", null));
        } catch (PaymentReceiptDataAccessException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new PaymentReceiptDataAccessException(
                    "Organization settings could not be read.", exception);
        }
    }

    private static PaymentReceiptSourceSnapshot mapSnapshot(ResultSet rs, int row)
            throws SQLException {
        return new PaymentReceiptSourceSnapshot(
                rs.getObject("id", UUID.class),
                rs.getString("payment_code"),
                PaymentStatus.valueOf(rs.getString("status")),
                rs.getString("client_code"),
                rs.getString("client_display_name"),
                rs.getString("membership_code"),
                rs.getString("plan_name_snapshot"),
                rs.getString("promotion_name_snapshot"),
                rs.getShort("period_number"),
                rs.getObject("starts_on", java.time.LocalDate.class),
                rs.getObject("effective_ends_on", java.time.LocalDate.class),
                rs.getBigDecimal("list_price"),
                rs.getBigDecimal("discount_amount"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                PaymentMethod.valueOf(rs.getString("payment_method")),
                instant(rs, "paid_at"));
    }

    private static PaymentReceiptOrganization mapOrganization(ResultSet rs, int row)
            throws SQLException {
        return new PaymentReceiptOrganization(
                rs.getString("display_name"),
                rs.getString("legal_name"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("address"),
                rs.getString("time_zone"));
    }

    private static java.time.Instant instant(ResultSet rs, String column)
            throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static void requireIdentifier(UUID value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
    }
}
