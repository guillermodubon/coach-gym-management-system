package io.github.guillermodubon.coachgym.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public user-module contract for resolving active notification recipients. */
public interface ActiveStaffDirectory {

    Optional<ActiveStaffMember> findActiveById(UUID userId);

    List<ActiveStaffMember> findActiveByRole(RoleCode roleCode);
}
