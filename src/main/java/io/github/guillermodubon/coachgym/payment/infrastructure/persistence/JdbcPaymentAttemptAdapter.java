package io.github.guillermodubon.coachgym.payment.infrastructure.persistence;

import io.github.guillermodubon.coachgym.payment.PaymentAttemptDetails;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptFailureCode;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptStatus;
import io.github.guillermodubon.coachgym.payment.PaymentProvider;
import io.github.guillermodubon.coachgym.payment.application.CancelPaymentAttemptPersistenceCommand;
import io.github.guillermodubon.coachgym.payment.application.FailPaymentAttemptCommand;
import io.github.guillermodubon.coachgym.payment.application.PaymentAttemptStore;
import io.github.guillermodubon.coachgym.payment.application.PaymentAttemptProviderDetails;
import io.github.guillermodubon.coachgym.payment.application.PaymentAttemptVersionConflictException;
import io.github.guillermodubon.coachgym.payment.application.ConfirmProviderPaymentAttemptCommand;
import io.github.guillermodubon.coachgym.payment.application.PersistPaymentAttemptCommand;
import io.github.guillermodubon.coachgym.payment.application.ProcessPaymentAttemptCommand;
import io.github.guillermodubon.coachgym.payment.application.ProviderPaymentFailureCommand;
import io.github.guillermodubon.coachgym.payment.domain.PaymentAttemptStateConflictException;
import io.github.guillermodubon.coachgym.payment.domain.PaymentAttemptTransitionPolicy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcPaymentAttemptAdapter implements PaymentAttemptStore {

    private static final String ATTEMPT_SELECT = """
            select id, client_id, membership_id, membership_period_id, provider, status,
                   expected_amount, currency, failure_code, confirmed_payment_id,
                   created_by_user_id, created_at, updated_at, completed_at, version,
                   checkout_reference, checkout_expires_at, initiated_at_branch_id
            from gym.payment_attempts
            where id = :id
            """;

    private static final String ATTEMPT_LOCK_SELECT = ATTEMPT_SELECT + " for update";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcPaymentAttemptAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public PaymentAttemptDetails create(PersistPaymentAttemptCommand command) {
        requireCommand(command);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", command.paymentAttemptId())
                .addValue("clientId", command.clientId())
                .addValue("membershipId", command.membershipId())
                .addValue("periodId", command.membershipPeriodId())
                .addValue("provider", command.provider().name())
                .addValue("amount", command.expectedAmount())
                .addValue("currency", command.currency().strip().toUpperCase())
                .addValue("userId", command.createdByUserId())
                .addValue("branchId", command.initiatedAtBranchId())
                .addValue("occurredAt", offset(command.occurredAt()));

        jdbcTemplate.update("""
                insert into gym.payment_attempts
                    (id, client_id, membership_id, membership_period_id, provider, status,
                     expected_amount, currency, created_by_user_id, created_at, updated_at,
                     version, initiated_at_branch_id)
                values (:id, :clientId, :membershipId, :periodId, :provider, 'CREATED',
                        :amount, :currency, :userId, :occurredAt, :occurredAt, 0,
                        COALESCE(:branchId, '7b0bf7d5-5184-43d2-8f9a-200000000002'::uuid))
                """, parameters);
        jdbcTemplate.update("""
                insert into gym.payment_attempt_status_history
                    (id, payment_attempt_id, previous_status, new_status, failure_code,
                     change_source, changed_by_user_id, occurred_at)
                values (:historyId, :id, null, 'CREATED', null, 'STAFF', :userId, :occurredAt)
                """, parameters.addValue("historyId", UUID.randomUUID()));

        return findById(command.paymentAttemptId()).orElseThrow();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentAttemptDetails> findById(UUID paymentAttemptId) {
        if (paymentAttemptId == null) {
            throw new IllegalArgumentException("Payment attempt id is required.");
        }
        return jdbcTemplate.query(ATTEMPT_SELECT,
                new MapSqlParameterSource("id", paymentAttemptId),
                JdbcPaymentAttemptAdapter::map).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentAttemptDetails> findById(UUID paymentAttemptId, UUID branchId) {
        if (paymentAttemptId == null) {
            throw new IllegalArgumentException("Payment attempt id is required.");
        }
        return jdbcTemplate.query(ATTEMPT_SELECT + " and initiated_at_branch_id = :branchId",
                new MapSqlParameterSource().addValue("id", paymentAttemptId)
                        .addValue("branchId", branchId),
                JdbcPaymentAttemptAdapter::map).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentAttemptProviderDetails> findProviderDetails(UUID paymentAttemptId) {
        if (paymentAttemptId == null) {
            throw new IllegalArgumentException("Payment attempt id is required.");
        }
        return jdbcTemplate.query(ATTEMPT_SELECT,
                new MapSqlParameterSource("id", paymentAttemptId),
                JdbcPaymentAttemptAdapter::mapProvider).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentAttemptProviderDetails> findProviderDetails(
            UUID paymentAttemptId, UUID branchId) {
        if (paymentAttemptId == null) {
            throw new IllegalArgumentException("Payment attempt id is required.");
        }
        return jdbcTemplate.query(
                ATTEMPT_SELECT + " and initiated_at_branch_id = :branchId",
                new MapSqlParameterSource().addValue("id", paymentAttemptId)
                        .addValue("branchId", branchId),
                JdbcPaymentAttemptAdapter::mapProvider).stream().findFirst();
    }

    @Override
    @Transactional
    public PaymentAttemptDetails markProcessing(ProcessPaymentAttemptCommand command) {
        requireProcessCommand(command);
        AttemptSnapshot current = lockAttempt(command.paymentAttemptId());
        requireExpectedVersion(current, command.expectedVersion());
        PaymentAttemptTransitionPolicy.requireTransitionAllowed(
                current.id(), current.status(), PaymentAttemptStatus.PROCESSING);
        if (current.checkoutReference() != null) {
            throw new PaymentAttemptStateConflictException(
                    current.id(), current.status(), PaymentAttemptStatus.PROCESSING);
        }

        int updated = jdbcTemplate.update("""
                update gym.payment_attempts
                set status = 'PROCESSING',
                    checkout_reference = :checkoutReference,
                    checkout_expires_at = :checkoutExpiresAt,
                    updated_at = :occurredAt,
                    version = version + 1
                where id = :id
                  and status = 'CREATED'
                  and version = :expectedVersion
                """, mutationParameters(command.paymentAttemptId(),
                command.expectedVersion(), command.occurredAt())
                .addValue("checkoutReference", command.checkoutReference())
                .addValue("checkoutExpiresAt", offset(command.checkoutExpiresAt())));
        requireUpdated(current, command.expectedVersion(), updated);
        insertHistory(current, PaymentAttemptStatus.PROCESSING, null,
                command.actorId(), command.occurredAt());
        return findById(current.id()).orElseThrow();
    }

    @Override
    @Transactional
    public PaymentAttemptDetails markFailed(FailPaymentAttemptCommand command) {
        requireFailCommand(command);
        AttemptSnapshot current = lockAttempt(command.paymentAttemptId());
        requireExpectedVersion(current, command.expectedVersion());
        PaymentAttemptTransitionPolicy.requireTransitionAllowed(
                current.id(), current.status(), PaymentAttemptStatus.FAILED);

        int updated = jdbcTemplate.update("""
                update gym.payment_attempts
                set status = 'FAILED',
                    failure_code = :failureCode,
                    completed_at = :occurredAt,
                    updated_at = :occurredAt,
                    version = version + 1
                where id = :id
                  and status = :previousStatus
                  and version = :expectedVersion
                """, mutationParameters(command.paymentAttemptId(),
                command.expectedVersion(), command.occurredAt())
                .addValue("previousStatus", current.status().name())
                .addValue("failureCode", command.failureCode().name()));
        requireUpdated(current, command.expectedVersion(), updated);
        insertHistory(current, PaymentAttemptStatus.FAILED,
                command.failureCode(), command.actorId(), command.occurredAt());
        return findById(current.id()).orElseThrow();
    }

    @Override
    @Transactional
    public PaymentAttemptDetails markCancelled(
            CancelPaymentAttemptPersistenceCommand command) {
        requireCancelCommand(command);
        AttemptSnapshot current = lockAttempt(command.paymentAttemptId());
        requireExpectedVersion(current, command.expectedVersion());
        PaymentAttemptTransitionPolicy.requireTransitionAllowed(
                current.id(), current.status(), PaymentAttemptStatus.CANCELLED);
        if (current.checkoutReference() == null) {
            throw new PaymentAttemptStateConflictException(
                    current.id(), current.status(), PaymentAttemptStatus.CANCELLED);
        }

        int updated = jdbcTemplate.update("""
                update gym.payment_attempts
                set status = 'CANCELLED',
                    failure_code = 'PROVIDER_CANCELLED',
                    completed_at = :occurredAt,
                    updated_at = :occurredAt,
                    version = version + 1
                where id = :id
                  and status = 'PROCESSING'
                  and version = :expectedVersion
                """, mutationParameters(command.paymentAttemptId(),
                command.expectedVersion(), command.occurredAt()));
        requireUpdated(current, command.expectedVersion(), updated);
        insertHistory(current, PaymentAttemptStatus.CANCELLED,
                PaymentAttemptFailureCode.PROVIDER_CANCELLED,
                command.actorId(), command.occurredAt());
        return findById(current.id()).orElseThrow();
    }

    @Override
    @Transactional
    public PaymentAttemptDetails markProviderSucceeded(
            ConfirmProviderPaymentAttemptCommand command) {
        requireProviderSuccessCommand(command);
        AttemptSnapshot current = lockAttempt(command.paymentAttemptId());
        requireExpectedVersion(current, command.expectedVersion());
        PaymentAttemptTransitionPolicy.requireTransitionAllowed(
                current.id(), current.status(), PaymentAttemptStatus.SUCCEEDED);

        int updated = jdbcTemplate.update("""
                update gym.payment_attempts
                set status = 'SUCCEEDED',
                    provider_payment_reference = :providerPaymentReference,
                    confirmed_payment_id = :confirmedPaymentId,
                    completed_at = :occurredAt,
                    updated_at = :occurredAt,
                    version = version + 1
                where id = :id
                  and status = 'PROCESSING'
                  and version = :expectedVersion
                """, mutationParameters(command.paymentAttemptId(),
                command.expectedVersion(), command.occurredAt())
                .addValue("providerPaymentReference", command.providerPaymentReference())
                .addValue("confirmedPaymentId", command.confirmedPaymentId()));
        requireUpdated(current, command.expectedVersion(), updated);
        insertHistory(current, PaymentAttemptStatus.SUCCEEDED, null, null,
                "PROVIDER", command.occurredAt());
        return findById(current.id()).orElseThrow();
    }

    @Override
    @Transactional
    public PaymentAttemptDetails markProviderFailure(
            ProviderPaymentFailureCommand command) {
        requireProviderFailureCommand(command);
        AttemptSnapshot current = lockAttempt(command.paymentAttemptId());
        requireExpectedVersion(current, command.expectedVersion());
        PaymentAttemptTransitionPolicy.requireTransitionAllowed(
                current.id(), current.status(), command.terminalStatus());

        int updated = jdbcTemplate.update("""
                update gym.payment_attempts
                set status = :newStatus,
                    failure_code = :failureCode,
                    completed_at = :occurredAt,
                    updated_at = :occurredAt,
                    version = version + 1
                where id = :id
                  and status = :previousStatus
                  and version = :expectedVersion
                """, mutationParameters(command.paymentAttemptId(),
                command.expectedVersion(), command.occurredAt())
                .addValue("newStatus", command.terminalStatus().name())
                .addValue("failureCode", command.failureCode().name())
                .addValue("previousStatus", current.status().name()));
        requireUpdated(current, command.expectedVersion(), updated);
        insertHistory(current, command.terminalStatus(), command.failureCode(), null,
                "PROVIDER", command.occurredAt());
        return findById(current.id()).orElseThrow();
    }

    private static PaymentAttemptDetails map(ResultSet resultSet, int row) throws SQLException {
        OffsetDateTime completedAt = resultSet.getObject("completed_at", OffsetDateTime.class);
        String failureCode = resultSet.getString("failure_code");
        return new PaymentAttemptDetails(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("client_id", UUID.class),
                resultSet.getObject("membership_id", UUID.class),
                resultSet.getObject("membership_period_id", UUID.class),
                PaymentProvider.valueOf(resultSet.getString("provider")),
                PaymentAttemptStatus.valueOf(resultSet.getString("status")),
                resultSet.getBigDecimal("expected_amount"),
                resultSet.getString("currency").strip(),
                failureCode == null ? null : PaymentAttemptFailureCode.valueOf(failureCode),
                resultSet.getObject("confirmed_payment_id", UUID.class),
                resultSet.getObject("created_by_user_id", UUID.class),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                resultSet.getObject("updated_at", OffsetDateTime.class).toInstant(),
                completedAt == null ? null : completedAt.toInstant(),
                resultSet.getLong("version"),
                resultSet.getObject("initiated_at_branch_id", UUID.class));
    }

    private static PaymentAttemptProviderDetails mapProvider(
            ResultSet resultSet,
            int row) throws SQLException {
        return new PaymentAttemptProviderDetails(
                map(resultSet, row), resultSet.getString("checkout_reference"));
    }

    private AttemptSnapshot lockAttempt(UUID paymentAttemptId) {
        return jdbcTemplate.query(ATTEMPT_LOCK_SELECT,
                new MapSqlParameterSource("id", paymentAttemptId),
                JdbcPaymentAttemptAdapter::mapSnapshot).stream().findFirst()
                .orElseThrow(() -> new io.github.guillermodubon.coachgym.payment.application
                        .PaymentAttemptNotFoundException(paymentAttemptId));
    }

    private void insertHistory(
            AttemptSnapshot current,
            PaymentAttemptStatus newStatus,
            PaymentAttemptFailureCode failureCode,
            UUID actorId,
            Instant occurredAt) {
        insertHistory(current, newStatus, failureCode, actorId, "STAFF", occurredAt);
    }

    private void insertHistory(
            AttemptSnapshot current,
            PaymentAttemptStatus newStatus,
            PaymentAttemptFailureCode failureCode,
            UUID actorId,
            String changeSource,
            Instant occurredAt) {
        jdbcTemplate.update("""
                insert into gym.payment_attempt_status_history
                    (id, payment_attempt_id, previous_status, new_status, failure_code,
                     change_source, changed_by_user_id, occurred_at)
                values (:historyId, :paymentAttemptId, :previousStatus, :newStatus,
                        :failureCode, :changeSource, :actorId, :occurredAt)
                """, new MapSqlParameterSource()
                .addValue("historyId", UUID.randomUUID())
                .addValue("paymentAttemptId", current.id())
                .addValue("previousStatus", current.status().name())
                .addValue("newStatus", newStatus.name())
                .addValue("failureCode", failureCode == null ? null : failureCode.name())
                .addValue("changeSource", changeSource)
                .addValue("actorId", actorId)
                .addValue("occurredAt", offset(occurredAt)));
    }

    private static MapSqlParameterSource mutationParameters(
            UUID paymentAttemptId,
            long expectedVersion,
            Instant occurredAt) {
        return new MapSqlParameterSource()
                .addValue("id", paymentAttemptId)
                .addValue("expectedVersion", expectedVersion)
                .addValue("occurredAt", offset(occurredAt));
    }

    private static void requireExpectedVersion(
            AttemptSnapshot current,
            long expectedVersion) {
        if (current.version() != expectedVersion) {
            throw new PaymentAttemptVersionConflictException(
                    current.id(), expectedVersion, current.version());
        }
    }

    private static void requireUpdated(
            AttemptSnapshot current,
            long expectedVersion,
            int updated) {
        if (updated != 1) {
            throw new PaymentAttemptVersionConflictException(
                    current.id(), expectedVersion, current.version());
        }
    }

    private static AttemptSnapshot mapSnapshot(ResultSet resultSet, int row)
            throws SQLException {
        return new AttemptSnapshot(
                resultSet.getObject("id", UUID.class),
                PaymentAttemptStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("checkout_reference"),
                resultSet.getLong("version"));
    }

    private static void requireProcessCommand(ProcessPaymentAttemptCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Process payment attempt command is required.");
        }
    }

    private static void requireFailCommand(FailPaymentAttemptCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Fail payment attempt command is required.");
        }
    }

    private static void requireCancelCommand(
            CancelPaymentAttemptPersistenceCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Cancel payment attempt command is required.");
        }
    }

    private static void requireProviderSuccessCommand(
            ConfirmProviderPaymentAttemptCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Provider success command is required.");
        }
    }

    private static void requireProviderFailureCommand(
            ProviderPaymentFailureCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Provider failure command is required.");
        }
    }

    private static void requireCommand(PersistPaymentAttemptCommand command) {
        if (command == null || command.paymentAttemptId() == null || command.clientId() == null
                || command.membershipId() == null || command.membershipPeriodId() == null
                || command.provider() == null || command.expectedAmount() == null
                || command.currency() == null || command.currency().isBlank()
                || command.createdByUserId() == null || command.occurredAt() == null) {
            throw new IllegalArgumentException("Complete payment attempt persistence data is required.");
        }
    }

    private static OffsetDateTime offset(java.time.Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record AttemptSnapshot(
            UUID id,
            PaymentAttemptStatus status,
            String checkoutReference,
            long version) {
    }
}
