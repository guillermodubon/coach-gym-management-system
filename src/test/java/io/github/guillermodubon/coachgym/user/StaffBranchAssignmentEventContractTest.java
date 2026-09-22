package io.github.guillermodubon.coachgym.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffBranchAssignmentEventContractTest {

    private static final UUID ASSIGNMENT_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID TARGET_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID ACTOR_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID BRANCH_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");

    @Test
    void eventsExposeOnlySafeIdentifiersTransitionFactsAndReasonPresence() {
        for (Class<?> eventType : new Class<?>[] {
            StaffBranchAssigned.class,
            StaffBranchAssignmentEnded.class,
            StaffScopeChanged.class
        }) {
            assertThat(eventType.isRecord()).isTrue();
            Arrays.stream(eventType.getRecordComponents())
                    .forEach(component -> assertThat(component.getName().toLowerCase())
                            .doesNotContain("password", "photo", "session", "token", "secret"));
        }

        StaffBranchAssigned assigned = new StaffBranchAssigned(
                ASSIGNMENT_ID, TARGET_ID, BRANCH_ID, ACTOR_ID,
                "organization-admin", NOW, true);
        StaffBranchAssignmentEnded ended = new StaffBranchAssignmentEnded(
                ASSIGNMENT_ID, TARGET_ID, BRANCH_ID, ACTOR_ID,
                "organization-admin", NOW, true);
        StaffScopeChanged scopeChanged = new StaffScopeChanged(
                TARGET_ID, StaffScopeType.BRANCH, StaffScopeType.ORGANIZATION,
                ACTOR_ID, "organization-admin", NOW, true);

        assertThat(assigned.reasonPresent()).isTrue();
        assertThat(ended.reasonPresent()).isTrue();
        assertThat(scopeChanged.reasonPresent()).isTrue();
        assertThat(assigned.toString()).doesNotContain("password", "photo", "session", "token");
    }

    @Test
    void eventsRejectSelfManagementAndNoOpScopeTransitions() {
        assertThatThrownBy(() -> new StaffBranchAssigned(
                ASSIGNMENT_ID, ACTOR_ID, BRANCH_ID, ACTOR_ID,
                "organization-admin", NOW, true))
                .isInstanceOf(StaffBranchAssignmentValidationException.class);
        assertThatThrownBy(() -> new StaffScopeChanged(
                TARGET_ID, StaffScopeType.BRANCH, StaffScopeType.BRANCH,
                ACTOR_ID, "organization-admin", NOW, true))
                .isInstanceOf(StaffScopeValidationException.class);
    }
}
