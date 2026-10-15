package io.github.guillermodubon.coachgym.reporting;

/** The only membership counts approved for the receptionist dashboard. */
public record ReceptionistMembershipSummary(
        long activeMemberships,
        long frozenMemberships,
        long expiringPeriods) {

    public ReceptionistMembershipSummary {
        if (activeMemberships < 0 || frozenMemberships < 0 || expiringPeriods < 0) {
            throw new IllegalArgumentException(
                    "Receptionist membership counts must be non-negative.");
        }
    }
}
