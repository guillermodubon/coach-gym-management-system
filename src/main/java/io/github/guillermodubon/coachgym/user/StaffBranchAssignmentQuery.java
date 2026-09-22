package io.github.guillermodubon.coachgym.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public read port for assignment history and active assignments. */
public interface StaffBranchAssignmentQuery {

    Optional<StaffBranchAssignmentDetails> findById(UUID assignmentId);

    StaffBranchAssignmentPage findPage(UUID userId, int page, int size);

    List<StaffBranchAssignmentDetails> findActive(UUID userId);

    List<StaffBranchAssignmentDetails> findActiveByBranch(UUID branchId);
}
