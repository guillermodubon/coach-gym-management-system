package io.github.guillermodubon.coachgym.plan;

import java.time.Instant;
import java.util.UUID;

/** Application event emitted after a plan mutation has been persisted. */
public record PlanChanged(
        UUID planId,
        String planCode,
        PlanChangeType changeType,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt) {
}
