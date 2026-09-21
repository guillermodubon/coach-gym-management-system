package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.organization.GymBranchCreated;
import io.github.guillermodubon.coachgym.organization.GymBranchStatus;
import io.github.guillermodubon.coachgym.organization.GymBranchStatusChanged;
import io.github.guillermodubon.coachgym.organization.GymBranchUpdated;
import io.github.guillermodubon.coachgym.organization.OrganizationUpdated;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrganizationAuditEntryJpaEntityTest {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-20T16:00:00Z");

    @Test
    void organizationUpdateStoresOnlyChangedFieldNames() {
        AuditEntryJpaEntity entry = AuditEntryJpaEntity.from(new OrganizationUpdated(
                ORGANIZATION_ID, "COACH_GYM", Set.of("brandName", "supportEmail"),
                ACTOR_ID, "admin", OCCURRED_AT));

        assertThat(entry.actionCode()).isEqualTo("ORGANIZATION_UPDATED");
        assertThat(entry.resourceType()).isEqualTo("ORGANIZATION");
        assertThat(entry.resourceId()).isEqualTo(ORGANIZATION_ID);
        assertThat(entry.resourceCodeSnapshot()).isEqualTo("COACH_GYM");
        assertThat(entry.metadata()).containsEntry(
                "changedFields", List.of("brandName", "supportEmail"));
        assertThat(entry.metadata().toString())
                .doesNotContain("legalName", "supportEmail@example", "address");
    }

    @Test
    void branchEventsStoreStableReferencesAndStatusTransitionOnly() {
        AuditEntryJpaEntity created = AuditEntryJpaEntity.from(new GymBranchCreated(
                BRANCH_ID, ORGANIZATION_ID, "NORTH", ACTOR_ID, "admin", OCCURRED_AT));
        AuditEntryJpaEntity updated = AuditEntryJpaEntity.from(new GymBranchUpdated(
                BRANCH_ID, ORGANIZATION_ID, "NORTH", Set.of("name"),
                ACTOR_ID, "admin", OCCURRED_AT));
        AuditEntryJpaEntity deactivated = AuditEntryJpaEntity.from(new GymBranchStatusChanged(
                BRANCH_ID, ORGANIZATION_ID, "NORTH", GymBranchStatus.ACTIVE,
                GymBranchStatus.INACTIVE, ACTOR_ID, "admin", OCCURRED_AT));

        assertThat(created.actionCode()).isEqualTo("GYM_BRANCH_CREATED");
        assertThat(updated.actionCode()).isEqualTo("GYM_BRANCH_UPDATED");
        assertThat(deactivated.actionCode()).isEqualTo("GYM_BRANCH_DEACTIVATED");
        assertThat(deactivated.metadata())
                .containsEntry("previousStatus", "ACTIVE")
                .containsEntry("newStatus", "INACTIVE")
                .containsEntry("organizationId", ORGANIZATION_ID.toString());
        assertThat(updated.metadata()).containsEntry("changedFields", List.of("name"));
        assertThat(deactivated.metadata().toString())
                .doesNotContain("address", "phone", "email", "reason");
    }
}
