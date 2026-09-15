package io.github.guillermodubon.coachgym.maintenance;

/**
 * Public lifecycle status of a maintenance work order.
 *
 * <p>{@link #COMPLETED} and {@link #CANCELLED} are terminal states.
 */
public enum MaintenanceStatus {
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    /** Returns whether no further maintenance lifecycle transition is allowed. */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
