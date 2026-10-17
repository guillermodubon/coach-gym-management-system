package io.github.guillermodubon.coachgym.user.infrastructure.persistence;

import io.github.guillermodubon.coachgym.shared.identityemail.IdentityEmailDeliveryStatus;
import io.github.guillermodubon.coachgym.shared.identityemail.StaffAccountActivatedEmail;
import io.github.guillermodubon.coachgym.user.application.StaffAccountActivationDeliveryClaim;
import io.github.guillermodubon.coachgym.user.application.StaffAccountActivationDeliveryStore;
import io.github.guillermodubon.coachgym.user.application.StaffAccountActivationRetryPolicy;
import io.github.guillermodubon.coachgym.user.application.StaffIdentityDataAccessException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL adapter for the privacy-minimal post-commit activation delivery outbox. */
@Repository
class JdbcStaffAccountActivationDeliveryAdapter
        implements StaffAccountActivationDeliveryStore {

    private final JdbcTemplate jdbcTemplate;

    JdbcStaffAccountActivationDeliveryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    @Transactional
    public void createPending(UUID invitationId) {
        if (invitationId == null) {
            throw new IllegalArgumentException("Invitation id is required for activation delivery.");
        }
        try {
            jdbcTemplate.update("""
                    insert into gym.staff_account_activation_deliveries (id, invitation_id)
                    values (?, ?)
                    """, UUID.randomUUID(), invitationId);
        } catch (DataAccessException failure) {
            throw StaffIdentityJdbcSupport.safeDataAccess(failure);
        }
    }

    @Override
    @Transactional
    public Optional<StaffAccountActivationDeliveryClaim> claim(
            UUID invitationId,
            Instant claimedAt,
            Instant leaseExpiresAt) {
        if (invitationId == null || claimedAt == null || leaseExpiresAt == null
                || !leaseExpiresAt.isAfter(claimedAt)) {
            throw new IllegalArgumentException("Activation delivery claim is invalid.");
        }
        try {
            jdbcTemplate.update("""
                    update gym.staff_account_activation_deliveries
                       set status = 'AMBIGUOUS',
                           lease_expires_at = null,
                           last_failure_code = 'OUTCOME_UNKNOWN',
                           version = version + 1
                     where invitation_id = ?
                       and status = 'SENDING'
                       and lease_expires_at <= ?
                    """, invitationId, StaffIdentityJdbcSupport.databaseTime(claimedAt));

            List<ClaimHeader> headers = jdbcTemplate.query("""
                    update gym.staff_account_activation_deliveries
                       set status = 'SENDING',
                           attempt_count = attempt_count + 1,
                           last_attempt_at = ?,
                           lease_expires_at = ?,
                           next_attempt_at = null,
                           sent_at = null,
                           last_failure_code = null,
                           version = version + 1
                     where invitation_id = ?
                       and attempt_count < ?
                       and (status = 'PENDING'
                            or (status = 'FAILED' and next_attempt_at <= ?))
                    returning id, attempt_count
                    """, (resultSet, rowNumber) -> new ClaimHeader(
                    StaffIdentityJdbcSupport.uuid(resultSet, "id"),
                    resultSet.getInt("attempt_count")),
                    StaffIdentityJdbcSupport.databaseTime(claimedAt),
                    StaffIdentityJdbcSupport.databaseTime(leaseExpiresAt),
                    invitationId,
                    StaffAccountActivationRetryPolicy.MAX_ATTEMPTS,
                    StaffIdentityJdbcSupport.databaseTime(claimedAt));
            if (headers.isEmpty()) {
                return Optional.empty();
            }
            ClaimHeader header = headers.getFirst();
            StaffAccountActivatedEmail email = jdbcTemplate.query("""
                    select u.email, u.first_name, u.last_name,
                           i.proposed_role, i.proposed_scope, i.accepted_at
                      from gym.staff_account_activation_deliveries d
                      join gym.staff_invitations i on i.id = d.invitation_id
                      join gym.users u on u.id = i.accepted_user_id
                     where d.id = ?
                       and d.status = 'SENDING'
                       and d.attempt_count = ?
                       and i.status = 'ACCEPTED'
                    """, (resultSet, rowNumber) -> activationEmail(resultSet),
                    header.deliveryId(), header.attemptNumber()).stream()
                    .findFirst()
                    .orElseThrow(() -> new StaffIdentityDataAccessException(
                            "Activation delivery source could not be resolved."));
            return Optional.of(new StaffAccountActivationDeliveryClaim(
                    header.deliveryId(), header.attemptNumber(), email));
        } catch (DataAccessException failure) {
            throw StaffIdentityJdbcSupport.safeDataAccess(failure);
        }
    }

    @Override
    @Transactional
    public void complete(
            UUID deliveryId,
            int attemptNumber,
            IdentityEmailDeliveryStatus outcome,
            Instant completedAt,
            Instant retryAt) {
        if (deliveryId == null || attemptNumber < 1 || outcome == null || completedAt == null
                || (outcome != IdentityEmailDeliveryStatus.FAILED && retryAt != null)
                || (retryAt != null && !retryAt.isAfter(completedAt))) {
            throw new IllegalArgumentException("Activation delivery result is invalid.");
        }
        String target = switch (outcome) {
            case SENT -> "SENT";
            case FAILED -> retryAt == null ? "EXHAUSTED" : "FAILED";
            case AMBIGUOUS -> "AMBIGUOUS";
        };
        String failure = switch (target) {
            case "FAILED", "EXHAUSTED" -> "DELIVERY_FAILED";
            case "AMBIGUOUS" -> "OUTCOME_UNKNOWN";
            default -> null;
        };
        try {
            int updated = jdbcTemplate.update("""
                    update gym.staff_account_activation_deliveries
                       set status = ?,
                           lease_expires_at = null,
                           next_attempt_at = ?,
                           sent_at = ?,
                           last_failure_code = ?,
                           version = version + 1
                     where id = ?
                       and status = 'SENDING'
                       and attempt_count = ?
                    """,
                    target,
                    retryAt == null ? null : StaffIdentityJdbcSupport.databaseTime(retryAt),
                    outcome == IdentityEmailDeliveryStatus.SENT
                            ? StaffIdentityJdbcSupport.databaseTime(completedAt) : null,
                    failure,
                    deliveryId,
                    attemptNumber);
            if (updated != 1) {
                throw new StaffIdentityDataAccessException(
                        "Activation delivery attempt could not be finalized.");
            }
        } catch (DataAccessException failureException) {
            throw StaffIdentityJdbcSupport.safeDataAccess(failureException);
        }
    }

    private static StaffAccountActivatedEmail activationEmail(ResultSet resultSet)
            throws SQLException {
        return new StaffAccountActivatedEmail(
                resultSet.getString("email"),
                resultSet.getString("first_name") + " " + resultSet.getString("last_name"),
                resultSet.getString("proposed_role"),
                resultSet.getString("proposed_scope"),
                StaffIdentityJdbcSupport.instant(resultSet, "accepted_at"));
    }

    private record ClaimHeader(UUID deliveryId, int attemptNumber) {
    }
}
