package io.github.guillermodubon.coachgym.user;

import java.util.UUID;

/** Public read port for resolving branch context without granting authorization. */
public interface ActiveBranchContextResolver {

    StaffBranchContext resolve(UUID userId);
}
