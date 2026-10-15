package io.github.guillermodubon.coachgym.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffBranchAssignmentPolicyTest {

    private static final UUID ASSIGNMENT_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID BRANCH_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Instant ASSIGNED_AT = Instant.parse("2026-09-21T12:00:00Z");

    @Test
    void creationRequiresAnActiveBranchAndNoDuplicate() {
        StaffBranchAssignmentPolicy.requireCreationAllowed(true, false);
        assertThatThrownBy(() -> StaffBranchAssignmentPolicy.requireCreationAllowed(false, false))
                .isInstanceOf(StaffBranchAssignmentStateConflictException.class);
        assertThatThrownBy(() -> StaffBranchAssignmentPolicy.requireCreationAllowed(true, true))
                .isInstanceOf(StaffBranchAssignmentStateConflictException.class);
    }

    @Test
    void endingIsAppendOnlyAndUsesMatchingOptimisticVersion() {
        StaffBranchAssignmentDetails active = activeAssignment(4);
        StaffBranchAssignmentPolicy.requireEndAllowed(
                active, new EndStaffBranchAssignmentCommand(ASSIGNMENT_ID, "left branch", 4));
        assertThatThrownBy(() -> StaffBranchAssignmentPolicy.requireEndAllowed(
                active, new EndStaffBranchAssignmentCommand(ASSIGNMENT_ID, "stale", 3)))
                .isInstanceOf(StaffBranchAssignmentStateConflictException.class);

        StaffBranchAssignmentDetails ended = new StaffBranchAssignmentDetails(
                ASSIGNMENT_ID, USER_ID, BRANCH_ID, StaffBranchAssignmentStatus.ENDED,
                ASSIGNED_AT, null, ASSIGNED_AT.plusSeconds(1),
                UUID.fromString("10000000-0000-0000-0000-000000000002"), "left branch", 5);
        assertThatThrownBy(() -> StaffBranchAssignmentPolicy.requireEndAllowed(
                ended, new EndStaffBranchAssignmentCommand(ASSIGNMENT_ID, "again", 5)))
                .isInstanceOf(StaffBranchAssignmentStateConflictException.class);
    }

    @Test
    void finalAssignmentCannotBeEndedWithoutAValidScopeTransition() {
        StaffAuthorizationContext target = new StaffAuthorizationContext(
                USER_ID, Set.of(RoleCode.RECEPTIONIST), StaffAccountStatus.ACTIVE,
                StaffScopeType.BRANCH, Set.of(BRANCH_ID));
        assertThatThrownBy(() -> StaffBranchAssignmentPolicy.requireActiveAssignmentRetained(
                target, false, false))
                .isInstanceOf(StaffBranchAssignmentStateConflictException.class);
        StaffBranchAssignmentPolicy.requireActiveAssignmentRetained(target, true, false);
        StaffBranchAssignmentPolicy.requireActiveAssignmentRetained(target, false, true);
    }

    @Test
    void assignmentDetailsEnforceLifecycleMetadata() {
        assertThat(activeAssignment(0).status()).isEqualTo(StaffBranchAssignmentStatus.ACTIVE);
        assertThatThrownBy(() -> new StaffBranchAssignmentDetails(
                ASSIGNMENT_ID, USER_ID, BRANCH_ID, StaffBranchAssignmentStatus.ACTIVE,
                ASSIGNED_AT, null, ASSIGNED_AT, null, "unexpected", 0))
                .isInstanceOf(StaffBranchAssignmentValidationException.class);
        assertThatThrownBy(() -> new StaffBranchAssignmentDetails(
                ASSIGNMENT_ID, USER_ID, BRANCH_ID, StaffBranchAssignmentStatus.ENDED,
                ASSIGNED_AT, null, ASSIGNED_AT.minusSeconds(1), null, "ended", 0))
                .isInstanceOf(StaffBranchAssignmentValidationException.class);
    }

    private static StaffBranchAssignmentDetails activeAssignment(long version) {
        return new StaffBranchAssignmentDetails(
                ASSIGNMENT_ID, USER_ID, BRANCH_ID, StaffBranchAssignmentStatus.ACTIVE,
                ASSIGNED_AT, null, null, null, null, version);
    }
}
