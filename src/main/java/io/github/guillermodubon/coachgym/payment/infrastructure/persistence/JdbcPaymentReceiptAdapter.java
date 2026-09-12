package io.github.guillermodubon.coachgym.payment.infrastructure.persistence;

import io.github.guillermodubon.coachgym.payment.PaymentMethod;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptDetails;
import io.github.guillermodubon.coachgym.payment.PaymentStatus;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptDataAccessException;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptDuplicateException;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptQuery;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptStore;
import io.github.guillermodubon.coachgym.payment.application.PaymentReceiptStorageKey;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcPaymentReceiptAdapter implements PaymentReceiptStore, PaymentReceiptQuery {

    static final String SELECT = """
            select id, receipt_number, payment_id, payment_code_snapshot,
                   payment_status_snapshot, client_code_snapshot,
                   client_display_name_snapshot, membership_code_snapshot,
                   plan_name_snapshot, promotion_name_snapshot,
                   membership_period_number, period_starts_on, period_ends_on,
                   list_price, discount_amount, amount, currency, payment_method,
                   paid_at, generated_at, generated_by_user_id,
                   generated_by_display_name, test_mode, content_type, size_bytes,
                   checksum_sha256, renderer_version, version
            from gym.payment_receipts
            """;

    static final String INSERT = """
            insert into gym.payment_receipts (
                id, receipt_number, payment_id, payment_code_snapshot,
                payment_status_snapshot, client_code_snapshot,
                client_display_name_snapshot, membership_code_snapshot,
                plan_name_snapshot, promotion_name_snapshot,
                membership_period_number, period_starts_on, period_ends_on,
                list_price, discount_amount, amount, currency, payment_method,
                paid_at, generated_at, generated_by_user_id,
                generated_by_display_name, test_mode, storage_key, content_type,
                size_bytes, checksum_sha256, renderer_version, version)
            values (
                :id, :receiptNumber, :paymentId, :paymentCode,
                :paymentStatus, :clientCode, :clientDisplayName, :membershipCode,
                :planName, :promotionName, :periodNumber, :periodStartsOn,
                :periodEndsOn, :listPrice, :discountAmount, :amount, :currency,
                :paymentMethod, :paidAt, :generatedAt, :generatedByUserId,
                :generatedByDisplayName, :testMode, :storageKey, :contentType,
                :sizeBytes, :checksumSha256, :rendererVersion, :version)
            on conflict (payment_id) do nothing
            returning id, receipt_number, payment_id, payment_code_snapshot,
                      payment_status_snapshot, client_code_snapshot,
                      client_display_name_snapshot, membership_code_snapshot,
                      plan_name_snapshot, promotion_name_snapshot,
                      membership_period_number, period_starts_on, period_ends_on,
                      list_price, discount_amount, amount, currency, payment_method,
                      paid_at, generated_at, generated_by_user_id,
                      generated_by_display_name, test_mode, content_type, size_bytes,
                      checksum_sha256, renderer_version, version
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcPaymentReceiptAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public PaymentReceiptDetails save(PaymentReceiptDetails details) {
        Objects.requireNonNull(details, "Receipt details are required.");
        try {
            List<PaymentReceiptDetails> rows = jdbcTemplate.query(
                    INSERT,
                    parameters(details),
                    JdbcPaymentReceiptAdapter::mapDetails);
            return rows.stream().findFirst().orElseThrow(() ->
                    new PaymentReceiptDuplicateException(details.paymentId()));
        } catch (DataIntegrityViolationException exception) {
            if (isUniqueViolation(exception)) {
                throw new PaymentReceiptDuplicateException(details.paymentId());
            }
            throw new PaymentReceiptDataAccessException(
                    "Payment receipt could not be persisted.", exception);
        } catch (DataAccessException exception) {
            throw new PaymentReceiptDataAccessException(
                    "Payment receipt could not be persisted.", exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentReceiptDetails> findById(UUID receiptId) {
        requireIdentifier(receiptId, "Receipt id");
        return find(SELECT + "where id = :id", "id", receiptId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentReceiptDetails> findByPaymentId(UUID paymentId) {
        requireIdentifier(paymentId, "Payment id");
        return find(SELECT + "where payment_id = :id", "id", paymentId);
    }

    private Optional<PaymentReceiptDetails> find(String sql, String parameter, UUID value) {
        try {
            return jdbcTemplate.query(
                    sql,
                    new MapSqlParameterSource(parameter, value),
                    JdbcPaymentReceiptAdapter::mapDetails)
                    .stream()
                    .findFirst();
        } catch (DataAccessException exception) {
            throw new PaymentReceiptDataAccessException(
                    "Payment receipt could not be read.", exception);
        }
    }

    private static MapSqlParameterSource parameters(PaymentReceiptDetails details) {
        return new MapSqlParameterSource()
                .addValue("id", details.id())
                .addValue("receiptNumber", details.receiptNumber())
                .addValue("paymentId", details.paymentId())
                .addValue("paymentCode", details.paymentCode())
                .addValue("paymentStatus", details.paymentStatus().name())
                .addValue("clientCode", details.clientCode())
                .addValue("clientDisplayName", details.clientDisplayName())
                .addValue("membershipCode", details.membershipCode())
                .addValue("planName", details.planName())
                .addValue("promotionName", details.promotionName())
                .addValue("periodNumber", details.membershipPeriodNumber())
                .addValue("periodStartsOn", details.periodStartsOn())
                .addValue("periodEndsOn", details.periodEndsOn())
                .addValue("listPrice", details.listPrice())
                .addValue("discountAmount", details.discountAmount())
                .addValue("amount", details.amount())
                .addValue("currency", details.currency())
                .addValue("paymentMethod", details.paymentMethod().name())
                .addValue("paidAt", offset(details.paidAt()))
                .addValue("generatedAt", offset(details.generatedAt()))
                .addValue("generatedByUserId", details.generatedByUserId())
                .addValue("generatedByDisplayName", details.generatedByDisplayName())
                .addValue("testMode", details.testMode())
                .addValue("storageKey", PaymentReceiptStorageKey.forReceipt(details.id()))
                .addValue("contentType", details.contentType())
                .addValue("sizeBytes", details.sizeBytes())
                .addValue("checksumSha256", details.checksumSha256())
                .addValue("rendererVersion", details.rendererVersion())
                .addValue("version", details.version());
    }

    private static PaymentReceiptDetails mapDetails(ResultSet rs, int row)
            throws SQLException {
        return new PaymentReceiptDetails(
                rs.getObject("id", UUID.class),
                rs.getString("receipt_number"),
                rs.getObject("payment_id", UUID.class),
                rs.getString("payment_code_snapshot"),
                PaymentStatus.valueOf(rs.getString("payment_status_snapshot")),
                rs.getString("client_code_snapshot"),
                rs.getString("client_display_name_snapshot"),
                rs.getString("membership_code_snapshot"),
                rs.getString("plan_name_snapshot"),
                rs.getString("promotion_name_snapshot"),
                rs.getShort("membership_period_number"),
                rs.getObject("period_starts_on", java.time.LocalDate.class),
                rs.getObject("period_ends_on", java.time.LocalDate.class),
                rs.getBigDecimal("list_price"),
                rs.getBigDecimal("discount_amount"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                PaymentMethod.valueOf(rs.getString("payment_method")),
                instant(rs, "paid_at"),
                instant(rs, "generated_at"),
                rs.getObject("generated_by_user_id", UUID.class),
                rs.getString("generated_by_display_name"),
                rs.getBoolean("test_mode"),
                rs.getString("content_type"),
                rs.getLong("size_bytes"),
                rs.getString("checksum_sha256"),
                rs.getString("renderer_version"),
                rs.getLong("version"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime offset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static void requireIdentifier(UUID value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
    }

    private static boolean isUniqueViolation(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && "23505".equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
