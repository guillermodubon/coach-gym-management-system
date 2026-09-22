package io.github.guillermodubon.coachgym.user.application;

import java.util.Optional;
import java.util.UUID;

/** Technology-neutral server-side storage for the selected branch preference. */
public interface ActiveBranchSessionStore {

    Optional<UUID> selectedBranchId();

    void select(UUID branchId);

    void clear();
}
