package io.github.guillermodubon.coachgym.organization;

/** Server-authorized organization lifecycle request with optimistic locking. */
public record ChangeOrganizationStatusCommand(
        OrganizationStatus requestedStatus,
        String reason,
        long expectedVersion) {

    public ChangeOrganizationStatusCommand {
        if (requestedStatus == null) {
            throw new OrganizationValidationException(
                    "Requested organization status is required.");
        }
        reason = OrganizationValuePolicy.reason(reason);
        OrganizationValuePolicy.version(expectedVersion, "Expected organization version");
    }
}
