package io.github.guillermodubon.coachgym.configuration;

import java.util.UUID;

/** Public authorization port implemented by the staff-ownership module. */
public interface AccessPaymentPolicyAuthorization {

    /** Requires active organization-admin authority and returns its organization id. */
    UUID requireOrganizationAdministrator(AccessPaymentPolicyActor actor);

    /** Requires admin read authority for this active branch and returns its organization id. */
    UUID requireAdministratorCanReadBranch(
            AccessPaymentPolicyActor actor,
            UUID branchId);

    /** Requires active ADMIN or assigned RECEPTIONIST operational read authority for a branch. */
    UUID requireOperationalStaffCanReadBranch(
            AccessPaymentPolicyActor actor,
            UUID branchId);
}
