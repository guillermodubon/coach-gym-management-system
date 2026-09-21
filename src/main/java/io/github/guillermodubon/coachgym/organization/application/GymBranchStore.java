package io.github.guillermodubon.coachgym.organization.application;

import io.github.guillermodubon.coachgym.organization.ChangeGymBranchStatusCommand;
import io.github.guillermodubon.coachgym.organization.CreateGymBranchCommand;
import io.github.guillermodubon.coachgym.organization.GymBranchDetails;
import io.github.guillermodubon.coachgym.organization.UpdateGymBranchCommand;
import java.time.Instant;
import java.util.UUID;

/** Write port for non-destructive canonical branch lifecycle data. */
public interface GymBranchStore {

    GymBranchDetails create(CreateGymBranchCommand command, Instant occurredAt);

    GymBranchDetails update(
            UUID id,
            UpdateGymBranchCommand command,
            Instant occurredAt);

    GymBranchDetails changeStatus(
            UUID id,
            ChangeGymBranchStatusCommand command,
            Instant occurredAt);
}
