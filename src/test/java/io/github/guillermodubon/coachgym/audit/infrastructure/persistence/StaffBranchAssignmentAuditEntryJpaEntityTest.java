package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.user.StaffBranchAssigned;
import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentEnded;
import io.github.guillermodubon.coachgym.user.StaffScopeChanged;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffBranchAssignmentAuditEntryJpaEntityTest {

    private static final UUID ASSIGNMENT_ID = UUID.randomUUID();
    private static final UUID TARGET_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-20T16:00:00Z");

    @Test
    void assignmentEventsPersistExactResourcesAndSafeTransitions() {
        AuditEntryJpaEntity assigned = AuditEntryJpaEntity.from(new StaffBranchAssigned(
                ASSIGNMENT_ID, TARGET_ID, BRANCH_ID, ACTOR_ID, "admin", OCCURRED_AT, true));
        AuditEntryJpaEntity ended = AuditEntryJpaEntity.from(new StaffBranchAssignmentEnded(
                ASSIGNMENT_ID, TARGET_ID, BRANCH_ID, ACTOR_ID, "admin", OCCURRED_AT, true));

        assertThat(assigned.actionCode()).isEqualTo("STAFF_BRANCH_ASSIGNED");
        assertThat(ended.actionCode()).isEqualTo("STAFF_BRANCH_ASSIGNMENT_ENDED");
        assertThat(assigned.resourceType()).isEqualTo("STAFF_BRANCH_ASSIGNMENT");
        assertThat(assigned.resourceId()).isEqualTo(ASSIGNMENT_ID);
        assertThat(assigned.actorUserId()).isEqualTo(ACTOR_ID);
        assertThat(assigned.metadata())
                .containsEntry("assignmentId", ASSIGNMENT_ID.toString())
                .containsEntry("targetUserId", TARGET_ID.toString())
                .containsEntry("branchId", BRANCH_ID.toString())
                .containsEntry("newStatus", "ACTIVE")
                .containsEntry("reasonPresent", true);
        assertThat(ended.metadata())
                .containsEntry("previousStatus", "ACTIVE")
                .containsEntry("newStatus", "ENDED");
        assertThat(assigned.metadata().toString()).doesNotContain("reasonText", "session");
    }

    @Test
    void scopeChangeUsesTargetAsResourceAndDoesNotPersistReasonText() {
        AuditEntryJpaEntity entry = AuditEntryJpaEntity.from(new StaffScopeChanged(
                TARGET_ID, StaffScopeType.ORGANIZATION, StaffScopeType.BRANCH,
                ACTOR_ID, "admin", OCCURRED_AT, true));

        assertThat(entry.actionCode()).isEqualTo("STAFF_SCOPE_CHANGED");
        assertThat(entry.resourceType()).isEqualTo("STAFF_SCOPE");
        assertThat(entry.resourceId()).isEqualTo(TARGET_ID);
        assertThat(entry.metadata())
                .containsEntry("targetUserId", TARGET_ID.toString())
                .containsEntry("previousScope", "ORGANIZATION")
                .containsEntry("newScope", "BRANCH")
                .containsEntry("reasonPresent", true);
        assertThat(entry.metadata().toString()).doesNotContain("reasonText", "session");
    }
}
