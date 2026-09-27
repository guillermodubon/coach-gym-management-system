package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.user.ChangeStaffRoleScopeCommand;
import io.github.guillermodubon.coachgym.user.StaffIdentityStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Transactional persistence port for staff account state and authority mutations. */
public interface StaffIdentityAdministrationStore {

    Optional<StaffIdentityAdministrationState> find(UUID userId);

    StaffIdentityAdministrationState changeStatus(
            UUID userId,
            StaffIdentityStatus expectedStatus,
            StaffIdentityStatus requestedStatus,
            long expectedSecurityVersion,
            UUID actorUserId,
            Instant occurredAt);

    StaffIdentityAdministrationState changeRoleScope(
            ChangeStaffRoleScopeCommand command,
            long expectedScopeVersion,
            UUID actorUserId,
            Instant occurredAt);
}
