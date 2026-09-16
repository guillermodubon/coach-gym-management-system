package io.github.guillermodubon.coachgym.notification.infrastructure.persistence;

import io.github.guillermodubon.coachgym.notification.EmailAttemptResult;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryAttemptDetails;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryDetails;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryFailureCode;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryStatus;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryType;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryDataAccessException;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryDuplicateException;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryNotFoundException;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryPage;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryQuery;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliverySearchQuery;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliverySortDirection;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliverySortField;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryStateConflictException;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryStore;
import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryVersionConflictException;
import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryValuePolicy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JDBC adapter for durable email delivery state and append-only attempts. */
@Repository
class JdbcTransactionalEmailDeliveryAdapter
        implements EmailDeliveryStore, EmailDeliveryQuery {

    private static final String COLUMNS = """
            id, delivery_type, source_resource_id, client_id,
            recipient_snapshot, subject_snapshot, template_version,
            attachment_resource_type, attachment_resource_id,
            attachment_filename, attachment_content_type,
            attachment_size_bytes, attachment_checksum_sha256,
            idempotency_key_digest, status, attempt_count,
            last_failure_code, last_failure_message, requested_at,
            requested_by_user_id, sent_at, last_attempt_at, created_at,
            updated_at, version
            """;

    private static final String ATTEMPT_COLUMNS = """
            id, delivery_id, attempt_number, result, started_at,
            completed_at, failure_code, failure_message,
            attempted_by_user_id, provider_message_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcTransactionalEmailDeliveryAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    @Transactional
    public EmailDeliveryDetails createPending(EmailDeliveryDetails delivery) {
        Objects.requireNonNull(delivery, "Email delivery is required.");
        if (delivery.status() != EmailDeliveryStatus.PENDING || delivery.attemptCount() != 0) {
            throw new IllegalArgumentException("Only a canonical pending delivery can be created.");
        }
        String sql = """
                insert into gym.email_deliveries (
                    id, delivery_type, source_resource_id, client_id,
                    recipient_snapshot, subject_snapshot, template_version,
                    attachment_resource_type, attachment_resource_id,
                    attachment_filename, attachment_content_type,
                    attachment_size_bytes, attachment_checksum_sha256,
                    idempotency_key_digest, status, attempt_count,
                    last_failure_code, last_failure_message, requested_at,
                    requested_by_user_id, sent_at, last_attempt_at,
                    created_at, updated_at, version)
                values (
                    :id, :deliveryType, :sourceResourceId, :clientId,
                    :recipient, :subject, :templateVersion,
                    :attachmentType, :attachmentId, :filename, :contentType,
                    :attachmentSize, :attachmentChecksum, :idempotencyDigest,
                    :status, :attemptCount, :lastFailureCode,
                    :lastFailureMessage, :requestedAt, :requestedByUserId,
                    :sentAt, :lastAttemptAt, :createdAt, :updatedAt, :version)
                returning """ + " " + COLUMNS;
        try {
            return jdbcTemplate.query(sql, parameters(delivery),
                    JdbcTransactionalEmailDeliveryAdapter::mapDelivery)
                    .stream().findFirst().orElseThrow(() ->
                            new EmailDeliveryDataAccessException(
                                    "Email delivery could not be persisted.", null));
        } catch (DataIntegrityViolationException exception) {
            if (isUniqueViolation(exception)) {
                throw new EmailDeliveryDuplicateException();
            }
            throw dataAccess("Email delivery could not be persisted.", exception);
        } catch (DataAccessException exception) {
            throw dataAccess("Email delivery could not be persisted.", exception);
        }
    }

    @Override
    @Transactional
    public EmailDeliveryAttemptDetails appendAttempt(EmailDeliveryAttemptDetails attempt) {
        Objects.requireNonNull(attempt, "Email delivery attempt is required.");
        String sql = """
                insert into gym.email_delivery_attempts (
                    id, delivery_id, attempt_number, result, started_at,
                    completed_at, failure_code, failure_message,
                    attempted_by_user_id, provider_message_id)
                values (:id, :deliveryId, :attemptNumber, :result, :startedAt,
                        :completedAt, :failureCode, :failureMessage,
                        :attemptedByUserId, :providerMessageId)
                returning """ + " " + ATTEMPT_COLUMNS;
        try {
            return jdbcTemplate.query(sql, attemptParameters(attempt),
                    JdbcTransactionalEmailDeliveryAdapter::mapAttempt)
                    .stream().findFirst().orElseThrow(() ->
                            new EmailDeliveryDataAccessException(
                                    "Email delivery attempt could not be persisted.", null));
        } catch (DataIntegrityViolationException exception) {
            if (isUniqueViolation(exception)) {
                throw new EmailDeliveryDuplicateException();
            }
            throw dataAccess("Email delivery attempt could not be persisted.", exception);
        } catch (DataAccessException exception) {
            if (isSentDeliveryConflict(exception)) {
                throw new EmailDeliveryStateConflictException(
                        attempt.deliveryId(), EmailDeliveryStatus.SENT, null);
            }
            throw dataAccess("Email delivery attempt could not be persisted.", exception);
        }
    }

    @Override
    @Transactional
    public EmailDeliveryDetails appendAttemptAndFinalize(
            EmailDeliveryAttemptDetails attempt,
            EmailDeliveryStatus status,
            EmailDeliveryFailureCode failureCode,
            String failureMessage,
            Instant attemptedAt,
            Instant sentAt,
            long expectedVersion) {
        Objects.requireNonNull(attempt, "Email delivery attempt is required.");
        if (attemptedAt == null || !attemptedAt.equals(attempt.completedAt())) {
            throw new IllegalArgumentException(
                    "Email attempt finalization timestamp must match the attempt.");
        }
        // These self-invocations deliberately share the transaction opened by
        // this method. Any exception rolls back both the append and the
        // optimistic-lock finalization as one durable outcome.
        appendAttempt(attempt);
        return finalizeAttempt(
                attempt.deliveryId(), status, failureCode, failureMessage,
                attemptedAt, sentAt, expectedVersion);
    }

    @Override
    @Transactional
    public EmailDeliveryDetails finalizeAttempt(
            UUID deliveryId,
            EmailDeliveryStatus status,
            EmailDeliveryFailureCode failureCode,
            String failureMessage,
            Instant attemptedAt,
            Instant sentAt,
            long expectedVersion) {
        requireIdentifier(deliveryId);
        if (status == null || status == EmailDeliveryStatus.PENDING) {
            throw new IllegalArgumentException("Final email delivery status is invalid.");
        }
        if (attemptedAt == null || expectedVersion < 0) {
            throw new IllegalArgumentException("Email finalization metadata is invalid.");
        }
        if (status == EmailDeliveryStatus.SENT && (failureCode != null || failureMessage != null
                || sentAt == null)) {
            throw new IllegalArgumentException("Sent email delivery metadata is invalid.");
        }
        if (status == EmailDeliveryStatus.FAILED && (failureCode == null
                || failureMessage == null || sentAt != null)) {
            throw new IllegalArgumentException("Failed email delivery metadata is invalid.");
        }
        String normalizedFailure = failureMessage == null
                ? null : EmailDeliveryValuePolicy.normalizeFailureMessage(failureMessage);
        String sql = """
                update gym.email_deliveries
                set status = :status,
                    last_failure_code = :failureCode,
                    last_failure_message = :failureMessage,
                    last_attempt_at = :attemptedAt,
                    sent_at = :sentAt,
                    attempt_count = attempt_count + 1,
                    version = version + 1
                where id = :id and version = :expectedVersion
                returning """ + " " + COLUMNS;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("status", status.name())
                .addValue("failureCode", failureCode == null ? null : failureCode.name())
                .addValue("failureMessage", normalizedFailure)
                .addValue("attemptedAt", offset(attemptedAt))
                .addValue("sentAt", sentAt == null ? null : offset(sentAt))
                .addValue("id", deliveryId)
                .addValue("expectedVersion", expectedVersion);
        try {
            List<EmailDeliveryDetails> rows = jdbcTemplate.query(
                    sql, parameters, JdbcTransactionalEmailDeliveryAdapter::mapDelivery);
            if (!rows.isEmpty()) {
                return rows.get(0);
            }
            return resolveFailedFinalization(deliveryId, status, expectedVersion);
        } catch (DataAccessException exception) {
            throw dataAccess("Email delivery could not be finalized.", exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmailDeliveryDetails> findById(UUID deliveryId) {
        requireIdentifier(deliveryId);
        return find("where id = :id", new MapSqlParameterSource("id", deliveryId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmailDeliveryDetails> findByIdempotencyKeyDigest(String digest) {
        String normalized = EmailDeliveryValuePolicy.normalizeDigest(digest);
        return find("where idempotency_key_digest = :digest",
                new MapSqlParameterSource("digest", normalized));
    }

    @Override
    @Transactional(readOnly = true)
    public EmailDeliveryPage findAll(EmailDeliverySearchQuery query) {
        Objects.requireNonNull(query, "Email delivery search query is required.");
        StringBuilder where = new StringBuilder(" where 1 = 1");
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (query.deliveryType() != null) {
            where.append(" and delivery_type = :deliveryType");
            parameters.addValue("deliveryType", query.deliveryType().name());
        }
        if (query.status() != null) {
            where.append(" and status = :status");
            parameters.addValue("status", query.status().name());
        }
        if (query.clientId() != null) {
            where.append(" and client_id = :clientId");
            parameters.addValue("clientId", query.clientId());
        }
        if (query.sourceResourceId() != null) {
            where.append(" and source_resource_id = :sourceResourceId");
            parameters.addValue("sourceResourceId", query.sourceResourceId());
        }
        if (query.requestedFrom() != null) {
            where.append(" and requested_at >= :requestedFrom");
            parameters.addValue("requestedFrom", offset(query.requestedFrom()));
        }
        if (query.requestedUntil() != null) {
            where.append(" and requested_at <= :requestedUntil");
            parameters.addValue("requestedUntil", offset(query.requestedUntil()));
        }
        String orderColumn = sortColumn(query.sortField());
        String direction = query.sortDirection() == EmailDeliverySortDirection.ASC ? "asc" : "desc";
        String order = " order by " + orderColumn + " " + direction
                + ", id " + direction;
        parameters.addValue("limit", query.size());
        parameters.addValue("offset", (long) query.page() * query.size());
        try {
            List<EmailDeliveryDetails> items = jdbcTemplate.query(
                    "select " + COLUMNS + " from gym.email_deliveries"
                            + where + order + " limit :limit offset :offset",
                    parameters, JdbcTransactionalEmailDeliveryAdapter::mapDelivery);
            Long total = jdbcTemplate.queryForObject(
                    "select count(*) from gym.email_deliveries" + where,
                    parameters, Long.class);
            long totalElements = total == null ? 0L : total;
            int totalPages = totalElements == 0
                    ? 0 : (int) ((totalElements + query.size() - 1) / query.size());
            return new EmailDeliveryPage(items, query.page(), query.size(), totalElements, totalPages);
        } catch (DataAccessException exception) {
            throw dataAccess("Email delivery history could not be read.", exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmailDeliveryAttemptDetails> findAttempts(UUID deliveryId) {
        requireIdentifier(deliveryId);
        try {
            return jdbcTemplate.query(
                    "select " + ATTEMPT_COLUMNS
                            + " from gym.email_delivery_attempts"
                            + " where delivery_id = :deliveryId"
                            + " order by attempt_number asc, id asc",
                    new MapSqlParameterSource("deliveryId", deliveryId),
                    JdbcTransactionalEmailDeliveryAdapter::mapAttempt);
        } catch (DataAccessException exception) {
            throw dataAccess("Email delivery attempts could not be read.", exception);
        }
    }

    private Optional<EmailDeliveryDetails> find(
            String predicate, MapSqlParameterSource parameters) {
        try {
            return jdbcTemplate.query(
                    "select " + COLUMNS + " from gym.email_deliveries " + predicate,
                    parameters, JdbcTransactionalEmailDeliveryAdapter::mapDelivery)
                    .stream().findFirst();
        } catch (DataAccessException exception) {
            throw dataAccess("Email delivery could not be read.", exception);
        }
    }

    private EmailDeliveryDetails resolveFailedFinalization(
            UUID deliveryId, EmailDeliveryStatus requestedStatus, long expectedVersion) {
        Optional<EmailDeliveryDetails> current = findById(deliveryId);
        if (current.isEmpty()) {
            throw new EmailDeliveryNotFoundException(deliveryId);
        }
        EmailDeliveryDetails details = current.get();
        if (details.status() == EmailDeliveryStatus.SENT) {
            throw new EmailDeliveryStateConflictException(
                    deliveryId, details.status(), requestedStatus);
        }
        throw new EmailDeliveryVersionConflictException(
                deliveryId, expectedVersion, details.version());
    }

    private static MapSqlParameterSource parameters(EmailDeliveryDetails delivery) {
        return new MapSqlParameterSource()
                .addValue("id", delivery.id())
                .addValue("deliveryType", delivery.deliveryType().name())
                .addValue("sourceResourceId", delivery.sourceResourceId())
                .addValue("clientId", delivery.clientId())
                .addValue("recipient", delivery.recipientSnapshot())
                .addValue("subject", delivery.subjectSnapshot())
                .addValue("templateVersion", delivery.templateVersion())
                .addValue("attachmentType", delivery.attachmentResourceType())
                .addValue("attachmentId", delivery.attachmentResourceId())
                .addValue("filename", delivery.attachmentFilename())
                .addValue("contentType", delivery.attachmentContentType())
                .addValue("attachmentSize", delivery.attachmentSizeBytes())
                .addValue("attachmentChecksum", delivery.attachmentChecksumSha256())
                .addValue("idempotencyDigest", delivery.idempotencyKeyDigest())
                .addValue("status", delivery.status().name())
                .addValue("attemptCount", delivery.attemptCount())
                .addValue("lastFailureCode", delivery.lastFailureCode() == null
                        ? null : delivery.lastFailureCode().name())
                .addValue("lastFailureMessage", delivery.lastFailureMessage())
                .addValue("requestedAt", offset(delivery.requestedAt()))
                .addValue("requestedByUserId", delivery.requestedByUserId())
                .addValue("sentAt", delivery.sentAt() == null ? null : offset(delivery.sentAt()))
                .addValue("lastAttemptAt", delivery.lastAttemptAt() == null
                        ? null : offset(delivery.lastAttemptAt()))
                .addValue("createdAt", offset(delivery.createdAt()))
                .addValue("updatedAt", offset(delivery.updatedAt()))
                .addValue("version", delivery.version());
    }

    private static MapSqlParameterSource attemptParameters(EmailDeliveryAttemptDetails attempt) {
        return new MapSqlParameterSource()
                .addValue("id", attempt.id())
                .addValue("deliveryId", attempt.deliveryId())
                .addValue("attemptNumber", attempt.attemptNumber())
                .addValue("result", attempt.result().name())
                .addValue("startedAt", offset(attempt.startedAt()))
                .addValue("completedAt", offset(attempt.completedAt()))
                .addValue("failureCode", attempt.failureCode() == null
                        ? null : attempt.failureCode().name())
                .addValue("failureMessage", attempt.failureMessage())
                .addValue("attemptedByUserId", attempt.attemptedByUserId())
                .addValue("providerMessageId", attempt.providerMessageId());
    }

    private static EmailDeliveryDetails mapDelivery(ResultSet rs, int row) throws SQLException {
        return new EmailDeliveryDetails(
                rs.getObject("id", UUID.class),
                EmailDeliveryType.valueOf(rs.getString("delivery_type")),
                rs.getObject("source_resource_id", UUID.class),
                rs.getObject("client_id", UUID.class),
                rs.getString("recipient_snapshot"),
                rs.getString("subject_snapshot"),
                rs.getString("template_version"),
                rs.getString("attachment_resource_type"),
                rs.getObject("attachment_resource_id", UUID.class),
                rs.getString("attachment_filename"),
                rs.getString("attachment_content_type"),
                rs.getLong("attachment_size_bytes"),
                rs.getString("attachment_checksum_sha256"),
                rs.getString("idempotency_key_digest"),
                EmailDeliveryStatus.valueOf(rs.getString("status")),
                rs.getInt("attempt_count"),
                enumValue(rs.getString("last_failure_code"), EmailDeliveryFailureCode.class),
                rs.getString("last_failure_message"),
                instant(rs, "requested_at"),
                rs.getObject("requested_by_user_id", UUID.class),
                instant(rs, "sent_at"),
                instant(rs, "last_attempt_at"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                rs.getLong("version"));
    }

    private static EmailDeliveryAttemptDetails mapAttempt(ResultSet rs, int row)
            throws SQLException {
        return new EmailDeliveryAttemptDetails(
                rs.getObject("id", UUID.class),
                rs.getObject("delivery_id", UUID.class),
                rs.getInt("attempt_number"),
                EmailAttemptResult.valueOf(rs.getString("result")),
                instant(rs, "started_at"),
                instant(rs, "completed_at"),
                enumValue(rs.getString("failure_code"), EmailDeliveryFailureCode.class),
                rs.getString("failure_message"),
                rs.getObject("attempted_by_user_id", UUID.class),
                rs.getString("provider_message_id"));
    }

    private static <E extends Enum<E>> E enumValue(String value, Class<E> type) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime offset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static String sortColumn(EmailDeliverySortField field) {
        return switch (field) {
            case REQUESTED_AT -> "requested_at";
            case UPDATED_AT -> "updated_at";
            case STATUS -> "status";
            case DELIVERY_TYPE -> "delivery_type";
            case ID -> "id";
        };
    }

    private static void requireIdentifier(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Email delivery id is required.");
        }
    }

    private static EmailDeliveryDataAccessException dataAccess(String message, Throwable cause) {
        return new EmailDeliveryDataAccessException(message, cause);
    }

    private static boolean isUniqueViolation(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException exception && "23505".equals(exception.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isSentDeliveryConflict(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException exception && "55000".equals(exception.getSQLState())
                    && current.toString().toLowerCase(Locale.ROOT).contains("sent")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
