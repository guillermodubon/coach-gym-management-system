package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.user.ChangeStaffScopeCommand;
import io.github.guillermodubon.coachgym.user.StaffScopeDetails;
import java.time.Instant;
import java.util.UUID;

/** Write port for optimistic, lifecycle-safe staff-scope transitions. */
public interface StaffScopeStore {

    StaffScopeDetails update(
            ChangeStaffScopeCommand command,
            UUID actorUserId,
            Instant occurredAt);
}
