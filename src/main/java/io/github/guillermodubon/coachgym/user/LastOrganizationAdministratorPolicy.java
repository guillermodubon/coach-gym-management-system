package io.github.guillermodubon.coachgym.user;

/** Prevents lifecycle and authority changes from removing the final active organization admin. */
public final class LastOrganizationAdministratorPolicy {

    private LastOrganizationAdministratorPolicy() {
    }

    public static void requireAdministratorRemains(
            boolean targetIsActiveOrganizationAdministrator,
            boolean targetRemainsActiveOrganizationAdministrator,
            long activeOrganizationAdministratorCount) {
        if (activeOrganizationAdministratorCount < 0) {
            throw new StaffIdentityValidationException(
                    "Active organization administrator count must not be negative.");
        }
        if (targetIsActiveOrganizationAdministrator
                && !targetRemainsActiveOrganizationAdministrator
                && activeOrganizationAdministratorCount <= 1) {
            throw new StaffIdentityStateConflictException(
                    "The last active organization administrator cannot lose organization authority.");
        }
    }
}
