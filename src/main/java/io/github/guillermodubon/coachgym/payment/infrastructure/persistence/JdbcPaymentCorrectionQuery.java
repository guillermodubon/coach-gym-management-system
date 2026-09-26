package io.github.guillermodubon.coachgym.payment.infrastructure.persistence;

import io.github.guillermodubon.coachgym.payment.PaymentCorrectionDetails;
import io.github.guillermodubon.coachgym.payment.PaymentCorrectionType;
import io.github.guillermodubon.coachgym.payment.PaymentRefundDetails;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import io.github.guillermodubon.coachgym.payment.application.PaymentCorrectionDataAccessException;
import io.github.guillermodubon.coachgym.payment.application.PaymentCorrectionQuery;
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
class JdbcPaymentCorrectionQuery implements PaymentCorrectionQuery {

    static final String SQL = """
            select p.id, p.payment_code, p.status, p.version,
                   p.registered_at_branch_id,
                   h.reason, h.occurred_at, h.changed_by_user_id,
                   r.id as refund_id, r.amount as refund_amount,
                   r.currency as refund_currency, r.reason as refund_reason,
                   r.external_reference as refund_external_reference,
                   r.refunded_at, r.refunded_by_user_id
            from gym.payments p
            join lateral (
                select reason, occurred_at, changed_by_user_id, new_status
                from gym.payment_status_history
                where payment_id = p.id
                  and new_status in ('VOIDED', 'REFUNDED')
                order by occurred_at desc, id desc
                limit 1
            ) h on true
            left join gym.payment_refunds r on r.payment_id = p.id
            where p.id = :paymentId
              and p.status in ('VOIDED', 'REFUNDED')
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcPaymentCorrectionQuery(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentCorrectionDetails> findByPaymentId(UUID paymentId) {
        return findByPaymentId(paymentId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentCorrectionDetails> findByPaymentId(
            UUID paymentId, UUID branchId) {
        Objects.requireNonNull(paymentId, "Payment id is required.");
        try {
            List<PaymentCorrectionDetails> rows = jdbcTemplate.query(
                    SQL + " and (CAST(:branchId AS uuid) is null or p.registered_at_branch_id = :branchId)",
                    new MapSqlParameterSource()
                            .addValue("paymentId", paymentId)
                            .addValue("branchId", branchId),
                    JdbcPaymentCorrectionQuery::mapCorrection);
            return rows.stream().findFirst();
        } catch (DataAccessException exception) {
            throw new PaymentCorrectionDataAccessException(
                    "Payment correction could not be read.", exception);
        }
    }

    private static PaymentCorrectionDetails mapCorrection(
            ResultSet rs,
            int row) throws SQLException {
        UUID paymentId = rs.getObject("id", UUID.class);
        PaymentStatus currentStatus = PaymentStatus.valueOf(
                rs.getString("status"));
        OffsetDateTime occurredAt = rs.getObject(
                "occurred_at", OffsetDateTime.class);
        UUID actorId = rs.getObject("changed_by_user_id", UUID.class);

        if (currentStatus == PaymentStatus.VOIDED) {
            return PaymentCorrectionDetails.voided(
                    paymentId,
                    rs.getString("payment_code"),
                    rs.getString("reason"),
                    occurredAt.toInstant(),
                    actorId,
                    rs.getLong("version"),
                    rs.getObject("registered_at_branch_id", UUID.class));
        }

        PaymentRefundDetails refund = new PaymentRefundDetails(
                rs.getObject("refund_id", UUID.class),
                paymentId,
                rs.getBigDecimal("refund_amount"),
                rs.getString("refund_currency").strip(),
                rs.getString("refund_reason"),
                rs.getString("refund_external_reference"),
                rs.getObject("refunded_at", OffsetDateTime.class).toInstant(),
                rs.getObject("refunded_by_user_id", UUID.class));

        return new PaymentCorrectionDetails(
                paymentId,
                rs.getString("payment_code"),
                PaymentCorrectionType.REFUND,
                PaymentStatus.PAID,
                PaymentStatus.REFUNDED,
                rs.getString("reason"),
                occurredAt.toInstant(),
                actorId,
                rs.getLong("version"),
                refund,
                rs.getObject("registered_at_branch_id", UUID.class));
    }
}
