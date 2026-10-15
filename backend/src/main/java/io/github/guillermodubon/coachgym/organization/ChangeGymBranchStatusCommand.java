package io.github.guillermodubon.coachgym.organization;

/** Explicit, reasoned branch lifecycle request with optimistic locking. */
public record ChangeGymBranchStatusCommand(
        GymBranchStatus requestedStatus,
        String reason,
        long expectedVersion) {

    public ChangeGymBranchStatusCommand {
        if (requestedStatus == null) {
            throw new GymBranchValidationException("Requested branch status is required.");
        }
        reason = OrganizationValuePolicy.reason(reason);
        OrganizationValuePolicy.version(expectedVersion, "Expected branch version");
    }
}
