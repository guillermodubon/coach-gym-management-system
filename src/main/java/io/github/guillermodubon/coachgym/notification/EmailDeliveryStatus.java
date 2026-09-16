package io.github.guillermodubon.coachgym.notification;

/** Durable lifecycle states for one canonical logical email delivery. */
public enum EmailDeliveryStatus {
    PENDING,
    SENT,
    FAILED;

    /**
     * Returns whether the status may be finalized from the current status.
     * A failed retry may remain {@code FAILED} while appending a new attempt.
     */
    public boolean canTransitionTo(EmailDeliveryStatus target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case PENDING -> target == SENT || target == FAILED;
            case FAILED -> target == SENT || target == FAILED;
            case SENT -> false;
        };
    }
}
