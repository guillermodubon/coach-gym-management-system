package io.github.guillermodubon.coachgym.auth.application;

import java.time.Instant;
import java.util.UUID;

/** Internal atomic storage for the organization-admin reauthentication throttle. */
public interface AdminReauthenticationAttemptStore {

    /** Acquires an actor-scoped transaction lock and reports whether a check is allowed. */
    boolean beginCheck(UUID actorId, Instant now);

    void recordFailure(UUID actorId, Instant attemptedAt);

    void clearFailures(UUID actorId);
}
