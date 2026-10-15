package io.github.guillermodubon.coachgym.user.application;

import java.util.UUID;

/** Persistence boundary for invitation acceptance account/profile/scope/assignment writes. */
public interface StaffAccountProvisioningStore {

    boolean emailInUse(String normalizedEmail);

    /** Must participate in the caller transaction and roll back every provisioning write on failure. */
    void provision(StaffAccountProvisioningDraft draft);
}
