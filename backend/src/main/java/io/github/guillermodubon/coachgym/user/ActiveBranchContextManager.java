package io.github.guillermodubon.coachgym.user;

import java.util.UUID;

/** Public port for later branch-context preference management. */
public interface ActiveBranchContextManager {

    StaffBranchContext select(UUID userId, SelectActiveBranchCommand command);

    void clear(UUID userId);
}
