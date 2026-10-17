package io.github.guillermodubon.coachgym.user;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Privacy-safe event emitted after a staff member's organizational scope changes. */
public record StaffScopeChanged(
        UUID targetUserId,
        StaffScopeType previousScope,
        StaffScopeType newScope,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt,
        boolean reasonPresent) {

    public StaffScopeChanged {
        targetUserId = StaffAssignmentValuePolicy.requireId(targetUserId, "targetUserId");
        previousScope = Objects.requireNonNull(previousScope, "previousScope is required");
        newScope = Objects.requireNonNull(newScope, "newScope is required");
        actorUserId = StaffAssignmentValuePolicy.requireId(actorUserId, "actorUserId");
        actorIdentifier = StaffAssignmentValuePolicy.requireText(
                actorIdentifier, "actorIdentifier", 255);
        occurredAt = StaffAssignmentValuePolicy.requireInstant(occurredAt, "occurredAt");
        if (previousScope == newScope) {
            throw new StaffScopeValidationException("scope change must change the current scope");
        }
        if (actorUserId.equals(targetUserId)) {
            throw new StaffScopeStateConflictException(
                    "a staff member cannot change their own scope");
        }
    }
}
