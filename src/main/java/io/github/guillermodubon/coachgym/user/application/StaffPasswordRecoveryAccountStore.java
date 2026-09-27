package io.github.guillermodubon.coachgym.user.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Locks staff account state while recovery requests and resets are applied. */
public interface StaffPasswordRecoveryAccountStore {

    Optional<StaffPasswordRecoveryAccount> lockByNormalizedEmail(String normalizedEmail);

    Optional<StaffPasswordRecoveryAccount> lockById(UUID userId);

    void updatePassword(
            UUID userId,
            long expectedSecurityVersion,
            String encodedPassword,
            Instant occurredAt);
}
