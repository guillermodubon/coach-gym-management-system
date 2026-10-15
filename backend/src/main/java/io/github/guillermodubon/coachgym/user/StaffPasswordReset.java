package io.github.guillermodubon.coachgym.user;

import java.time.Instant;
import java.util.UUID;

/** Privacy-minimized fact emitted after a recovery password change commits. */
public record StaffPasswordReset(UUID userId, Instant occurredAt) {

    public StaffPasswordReset {
        if (userId == null || occurredAt == null) {
            throw new StaffIdentityValidationException("Password reset event is incomplete.");
        }
    }

    @Override
    public String toString() {
        return "StaffPasswordReset[userPresent=" + (userId != null)
                + ", occurredAt=" + occurredAt + ']';
    }
}
