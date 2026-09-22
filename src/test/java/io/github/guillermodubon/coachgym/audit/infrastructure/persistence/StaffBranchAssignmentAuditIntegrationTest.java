package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import io.github.guillermodubon.coachgym.user.StaffBranchAssigned;
import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentEnded;
import io.github.guillermodubon.coachgym.user.StaffScopeChanged;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

class StaffBranchAssignmentAuditIntegrationTest extends AbstractIncidentApiIntegrationTest {

    private static final UUID ASSIGNMENT_ID = UUID.randomUUID();
    private static final UUID TARGET_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void clearStaffAssignmentAuditEntries() {
        jdbcTemplate.update("""
                delete from gym.audit_entries
                where action_code in (
                    'STAFF_SCOPE_CHANGED',
                    'STAFF_BRANCH_ASSIGNED',
                    'STAFF_BRANCH_ASSIGNMENT_ENDED')
                """);
    }

    @Test
    void persistsScopeAndAssignmentLifecycleWithJsonbSafeMetadata() {
        Instant occurredAt = Instant.parse("2026-09-20T16:00:00Z");

        eventPublisher.publishEvent(new StaffBranchAssigned(
                ASSIGNMENT_ID, TARGET_ID, BRANCH_ID, adminId, ADMIN_USERNAME, occurredAt, true));
        eventPublisher.publishEvent(new StaffBranchAssignmentEnded(
                ASSIGNMENT_ID, TARGET_ID, BRANCH_ID, adminId, ADMIN_USERNAME, occurredAt, true));
        eventPublisher.publishEvent(new StaffScopeChanged(
                TARGET_ID, StaffScopeType.ORGANIZATION, StaffScopeType.BRANCH,
                adminId, ADMIN_USERNAME, occurredAt, true));

        List<String> actions = jdbcTemplate.queryForList("""
                select action_code
                from gym.audit_entries
                where action_code in (
                    'STAFF_SCOPE_CHANGED',
                    'STAFF_BRANCH_ASSIGNED',
                    'STAFF_BRANCH_ASSIGNMENT_ENDED')
                order by action_code
                """, String.class);

        assertThat(actions).containsExactly(
                "STAFF_BRANCH_ASSIGNED",
                "STAFF_BRANCH_ASSIGNMENT_ENDED",
                "STAFF_SCOPE_CHANGED");

        String metadata = jdbcTemplate.queryForObject("""
                select metadata::text
                from gym.audit_entries
                where action_code = 'STAFF_BRANCH_ASSIGNMENT_ENDED'
                """, String.class);
        assertThat(metadata)
                .contains("assignmentId", "targetUserId", "branchId", "previousStatus", "newStatus")
                .doesNotContain("password", "session", "reasonText", "address", "email");
    }
}
