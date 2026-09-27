package io.github.guillermodubon.coachgym.user.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Application port for user-account mutations.
 */
public interface UserAccountStore {

    boolean hasAnyUsers();

    /** Serializes the empty-registry check and creation across concurrent application starts. */
    void lockBootstrapLifecycle();

    UUID createInitialAdministrator(InitialAdministrator administrator, Instant grantedAt);

    void recordSuccessfulLogin(UUID userId, Instant occurredAt);
}
