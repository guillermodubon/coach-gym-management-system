package io.github.guillermodubon.coachgym.payment.infrastructure.persistence;

import io.github.guillermodubon.coachgym.payment.PaymentCorrectionDetails;
import io.github.guillermodubon.coachgym.payment.PaymentRefundDetails;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import io.github.guillermodubon.coachgym.payment.application.PaymentCorrectionDataAccessException;
import io.github.guillermodubon.coachgym.payment.application.PaymentCorrectionNotFoundException;
import io.github.guillermodubon.coachgym.payment.application.PaymentCorrectionPolicy;
import io.github.guillermodubon.coachgym.payment.application.PaymentCorrectionStateConflictException;
import io.github.guillermodubon.coachgym.payment.application.PaymentCorrectionStore;
import io.github.guillermodubon.coachgym.payment.application.PaymentCorrectionVersionConflictException;
import io.github.guillermodubon.coachgym.payment.application.PaymentRefundConflictException;
import io.github.guillermodubon.coachgym.payment.application.RefundPaymentCommand;
import io.github.guillermodubon.coachgym.payment.application.VoidPaymentCommand;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcPaymentCorrectionAdapter implements PaymentCorrectionStore {

    static final String LOCK_PAYMENT_SQL = """
            select id, payment_code, amount, currency, payment_method,
                   status, version
            from gym.payments
            where id = :paymentId
            for update
            """;

    static final String UPDATE_STATUS_SQL = """
            update gym.payments
            set status = :newStatus,
                updated_at = :occurredAt,
                version = version + 1
            where id = :paymentId
              and version = :expectedVersion
              and status = 'PAID'
            """;

    static final String INSERT_HISTORY_SQL = """
            insert into gym.payment_status_history
                (id, payment_id, previous_status, new_status,
                 reason, occurred_at, changed_by_user_id)
            values
                (:historyId, :paymentId, 'PAID', :newStatus,
                 :reason, :occurredAt, :actorId)
            """;

    static final String INSERT_REFUND_SQL = """
            insert into gym.payment_refunds
                (id, payment_id, amount, currency, refund_method,
                 reason, external_reference, refunded_at,
                 refunded_by_user_id, created_at)
            values
                (:refundId, :paymentId, :amount, :currency, :refundMethod,
                 :reason, :externalReference, :occurredAt,
                 :actorId, :occurredAt)
            returning refund_code
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcPaymentCorrectionAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    @Transactional
    public PaymentCorrectionDetails voidPayment(
            VoidPaymentCommand command,
            AuthenticatedActor actor,
            Instant occurredAt) {
        requireActorAndTime(actor, occurredAt);
        try {
            PaymentSnapshot payment = lockPayment(command.paymentId());
            PaymentCorrectionPolicy.requireExpectedVersion(
                    payment.id(), command.expectedVersion(), payment.version());
            PaymentCorrectionPolicy.requireVoidAllowed(
                    payment.id(), payment.status());

            updateStatus(payment.id(), command.expectedVersion(),
                    PaymentStatus.VOIDED, occurredAt);
            insertHistory(payment.id(), PaymentStatus.VOIDED,
                    command.reason(), actor.id(), occurredAt);

            return PaymentCorrectionDetails.voided(
                    payment.id(), payment.paymentCode(), command.reason(),
                    occurredAt, actor.id(), payment.version() + 1);
        } catch (PaymentCorrectionNotFoundException
                | PaymentCorrectionVersionConflictException
                | PaymentCorrectionStateConflictException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new PaymentCorrectionDataAccessException(
                    "Payment could not be voided.", exception);
        }
    }

    @Override
    @Transactional
    public PaymentCorrectionDetails refundPayment(
            RefundPaymentCommand command,
            AuthenticatedActor actor,
            Instant occurredAt) {
        requireActorAndTime(actor, occurredAt);
        try {
            PaymentSnapshot payment = lockPayment(command.paymentId());
            PaymentCorrectionPolicy.requireExpectedVersion(
                    payment.id(), command.expectedVersion(), payment.version());
            PaymentCorrectionPolicy.requireRefundAllowed(
                    payment.id(), payment.status());

            UUID refundId = UUID.randomUUID();
            String refundCode;
            try {
                refundCode = insertRefund(refundId, payment, command,
                        actor.id(), occurredAt);
            } catch (DataIntegrityViolationException exception) {
                if (refundExists(payment.id())) {
                    throw new PaymentRefundConflictException(payment.id());
                }
                throw exception;
            }

            updateStatus(payment.id(), command.expectedVersion(),
                    PaymentStatus.REFUNDED, occurredAt);
            insertHistory(payment.id(), PaymentStatus.REFUNDED,
                    command.reason(), actor.id(), occurredAt);

            PaymentRefundDetails refund = new PaymentRefundDetails(
                    refundId, payment.id(), payment.amount(), payment.currency(),
                    command.reason(), command.externalReference(), occurredAt,
                    actor.id());

            // Forces evaluation of the database-generated code and verifies
            // successful INSERT without exposing it in the public refund model.
            Objects.requireNonNull(refundCode, "Refund code was not generated.");

            return PaymentCorrectionDetails.refunded(
                    payment.id(), payment.paymentCode(), command.reason(),
                    occurredAt, actor.id(), payment.version() + 1, refund);
        } catch (PaymentCorrectionNotFoundException
                | PaymentCorrectionVersionConflictException
                | PaymentCorrectionStateConflictException
                | PaymentRefundConflictException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new PaymentCorrectionDataAccessException(
                    "Payment could not be refunded.", exception);
        }
    }

    private PaymentSnapshot lockPayment(UUID paymentId) {
        List<PaymentSnapshot> rows = jdbcTemplate.query(
                LOCK_PAYMENT_SQL,
                new MapSqlParameterSource("paymentId", paymentId),
                JdbcPaymentCorrectionAdapter::mapPayment);
        return rows.stream().findFirst()
                .orElseThrow(() ->
                        new PaymentCorrectionNotFoundException(paymentId));
    }

    private void updateStatus(
            UUID paymentId,
            long expectedVersion,
            PaymentStatus newStatus,
            Instant occurredAt) {
        int updated = jdbcTemplate.update(
                UPDATE_STATUS_SQL,
                new MapSqlParameterSource()
                        .addValue("paymentId", paymentId)
                        .addValue("expectedVersion", expectedVersion)
                        .addValue("newStatus", newStatus.name())
                        .addValue("occurredAt", offset(occurredAt)));
        if (updated != 1) {
            throw new PaymentCorrectionVersionConflictException(
                    paymentId, expectedVersion, expectedVersion + 1);
        }
    }

    private void insertHistory(
            UUID paymentId,
            PaymentStatus newStatus,
            String reason,
            UUID actorId,
            Instant occurredAt) {
        jdbcTemplate.update(
                INSERT_HISTORY_SQL,
                new MapSqlParameterSource()
                        .addValue("historyId", UUID.randomUUID())
                        .addValue("paymentId", paymentId)
                        .addValue("newStatus", newStatus.name())
                        .addValue("reason", reason)
                        .addValue("occurredAt", offset(occurredAt))
                        .addValue("actorId", actorId));
    }

    private String insertRefund(
            UUID refundId,
            PaymentSnapshot payment,
            RefundPaymentCommand command,
            UUID actorId,
            Instant occurredAt) {
        return jdbcTemplate.queryForObject(
                INSERT_REFUND_SQL,
                new MapSqlParameterSource()
                        .addValue("refundId", refundId)
                        .addValue("paymentId", payment.id())
                        .addValue("amount", payment.amount())
                        .addValue("currency", payment.currency())
                        .addValue("refundMethod", payment.paymentMethod())
                        .addValue("reason", command.reason())
                        .addValue("externalReference",
                                command.externalReference())
                        .addValue("occurredAt", offset(occurredAt))
                        .addValue("actorId", actorId),
                String.class);
    }

    private boolean refundExists(UUID paymentId) {
        Boolean exists = jdbcTemplate.queryForObject(
                """
                select exists (
                    select 1 from gym.payment_refunds
                    where payment_id = :paymentId
                )
                """,
                new MapSqlParameterSource("paymentId", paymentId),
                Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    private static PaymentSnapshot mapPayment(ResultSet rs, int row)
            throws SQLException {
        return new PaymentSnapshot(
                rs.getObject("id", UUID.class),
                rs.getString("payment_code"),
                rs.getBigDecimal("amount"),
                rs.getString("currency").strip(),
                rs.getString("payment_method"),
                PaymentStatus.valueOf(rs.getString("status")),
                rs.getLong("version"));
    }

    private static void requireActorAndTime(
            AuthenticatedActor actor,
            Instant occurredAt) {
        if (actor == null || actor.id() == null) {
            throw new IllegalArgumentException(
                    "Authenticated actor is required.");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException(
                    "Payment correction timestamp is required.");
        }
    }

    private static OffsetDateTime offset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record PaymentSnapshot(
            UUID id,
            String paymentCode,
            BigDecimal amount,
            String currency,
            String paymentMethod,
            PaymentStatus status,
            long version) {
    }
}
