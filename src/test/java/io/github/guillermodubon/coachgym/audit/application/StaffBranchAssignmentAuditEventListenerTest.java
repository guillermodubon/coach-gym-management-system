package io.github.guillermodubon.coachgym.audit.application;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.github.guillermodubon.coachgym.user.StaffBranchAssigned;
import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentEnded;
import io.github.guillermodubon.coachgym.user.StaffScopeChanged;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class StaffBranchAssignmentAuditEventListenerTest {

    private static final UUID ASSIGNMENT_ID = UUID.randomUUID();
    private static final UUID TARGET_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-20T16:00:00Z");

    @Test
    void forwardsEachStaffLifecycleEventExactlyOnce() {
        AuditEntryStore store = Mockito.mock(AuditEntryStore.class);
        StaffBranchAssignmentAuditEventListener listener =
                new StaffBranchAssignmentAuditEventListener(store);
        StaffBranchAssigned assigned = new StaffBranchAssigned(
                ASSIGNMENT_ID, TARGET_ID, BRANCH_ID, ACTOR_ID, "admin", OCCURRED_AT, true);
        StaffBranchAssignmentEnded ended = new StaffBranchAssignmentEnded(
                ASSIGNMENT_ID, TARGET_ID, BRANCH_ID, ACTOR_ID, "admin", OCCURRED_AT, true);
        StaffScopeChanged scopeChanged = new StaffScopeChanged(
                TARGET_ID, StaffScopeType.ORGANIZATION, StaffScopeType.BRANCH,
                ACTOR_ID, "admin", OCCURRED_AT, true);

        listener.record(assigned);
        listener.record(ended);
        listener.record(scopeChanged);

        verify(store, times(1)).recordStaffBranchAssigned(assigned);
        verify(store, times(1)).recordStaffBranchAssignmentEnded(ended);
        verify(store, times(1)).recordStaffScopeChanged(scopeChanged);
    }
}
