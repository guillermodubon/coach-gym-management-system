package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.user.PasswordRecoveryStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Internal persistence port for password-recovery token lifecycle records. */
public interface StaffPasswordRecoveryPersistence {

    StaffPasswordRecoveryRecord create(StaffPasswordRecoveryDraft draft);

    Optional<StaffPasswordRecoveryRecord> findPendingByFingerprint(
            StaffTokenFingerprint fingerprint);

    Optional<StaffPasswordRecoveryRecord> lockPendingByFingerprint(
            StaffTokenFingerprint fingerprint);

    void revokePendingForUser(UUID userId, Instant occurredAt);

    StaffPasswordRecoveryRecord transition(
            UUID recoveryId,
            PasswordRecoveryStatus target,
            Instant occurredAt,
            long expectedVersion);

    StaffPasswordRecoveryRecord recordFailedAttempt(
            UUID recoveryId,
            Instant occurredAt,
            long expectedVersion);
}
