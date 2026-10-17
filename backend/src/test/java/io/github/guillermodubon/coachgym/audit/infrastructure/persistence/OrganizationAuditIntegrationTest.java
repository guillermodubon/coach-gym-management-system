package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import io.github.guillermodubon.coachgym.organization.GymBranchCreated;
import io.github.guillermodubon.coachgym.organization.GymBranchStatus;
import io.github.guillermodubon.coachgym.organization.GymBranchStatusChanged;
import io.github.guillermodubon.coachgym.organization.GymBranchUpdated;
import io.github.guillermodubon.coachgym.organization.OrganizationUpdated;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

class OrganizationAuditIntegrationTest extends AbstractIncidentApiIntegrationTest {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("7b0bf7d5-5184-43d2-8f9a-200000000001");
    private static final UUID BRANCH_ID = UUID.randomUUID();

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void clearOrganizationAuditEntries() {
        jdbcTemplate.update(
                "delete from gym.audit_entries where resource_type in ('ORGANIZATION', 'GYM_BRANCH')");
    }

    @Test
    void persistsOrganizationAndBranchLifecycleAuditWithoutPiiHeavyMetadata() {
        UUID actorId = adminId;
        Instant occurredAt = Instant.parse("2026-09-20T16:00:00Z");

        eventPublisher.publishEvent(new OrganizationUpdated(
                ORGANIZATION_ID, "COACH_GYM", Set.of("brandName"),
                actorId, ADMIN_USERNAME, occurredAt));
        eventPublisher.publishEvent(new GymBranchCreated(
                BRANCH_ID, ORGANIZATION_ID, "NORTH", actorId, ADMIN_USERNAME, occurredAt));
        eventPublisher.publishEvent(new GymBranchUpdated(
                BRANCH_ID, ORGANIZATION_ID, "NORTH", Set.of("name"),
                actorId, ADMIN_USERNAME, occurredAt));
        eventPublisher.publishEvent(new GymBranchStatusChanged(
                BRANCH_ID, ORGANIZATION_ID, "NORTH", GymBranchStatus.ACTIVE,
                GymBranchStatus.INACTIVE, actorId, ADMIN_USERNAME, occurredAt));

        List<String> actions = jdbcTemplate.queryForList("""
                select action_code
                from gym.audit_entries
                where resource_type in ('ORGANIZATION', 'GYM_BRANCH')
                order by action_code
                """, String.class);

        assertThat(actions).containsExactly(
                "GYM_BRANCH_CREATED",
                "GYM_BRANCH_DEACTIVATED",
                "GYM_BRANCH_UPDATED",
                "ORGANIZATION_UPDATED");

        String metadata = jdbcTemplate.queryForObject("""
                select metadata::text
                from gym.audit_entries
                where action_code = 'GYM_BRANCH_DEACTIVATED'
                """, String.class);
        assertThat(metadata)
                .contains("previousStatus", "newStatus", "organizationId")
                .doesNotContain("address", "phone", "email", "reason");
    }
}
