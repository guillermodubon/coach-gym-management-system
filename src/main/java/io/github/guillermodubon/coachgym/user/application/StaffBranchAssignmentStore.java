package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.user.AssignStaffToBranchCommand;
import io.github.guillermodubon.coachgym.user.EndStaffBranchAssignmentCommand;
import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentDetails;
import java.time.Instant;
import java.util.UUID;

/** Write port for append-only staff-to-branch assignment lifecycle data. */
public interface StaffBranchAssignmentStore {

    StaffBranchAssignmentDetails assign(
            AssignStaffToBranchCommand command,
            UUID actorUserId,
            Instant occurredAt);

    StaffBranchAssignmentDetails end(
            EndStaffBranchAssignmentCommand command,
            UUID actorUserId,
            Instant occurredAt);
}
