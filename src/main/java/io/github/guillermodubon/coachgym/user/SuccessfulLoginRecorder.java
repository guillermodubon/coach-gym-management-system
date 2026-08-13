package io.github.guillermodubon.coachgym.user;

import java.time.Instant;
import java.util.UUID;

/**
 * Public user-module API for recording a successful authentication event.
 */
public interface SuccessfulLoginRecorder {

    void recordSuccessfulLogin(UUID userId, Instant occurredAt);
}
