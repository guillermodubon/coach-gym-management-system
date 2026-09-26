package io.github.guillermodubon.coachgym.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.plan.MembershipPlanBranchCoverageScope;
import io.github.guillermodubon.coachgym.plan.MembershipPlanCoverageChanged;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembershipPlanCoverageAuditEntryJpaEntityTest {

    @Test
    void mapsOnlyCoverageSummaryAndActorSnapshotToAuditMetadata() {
        UUID planId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-09-24T14:00:00Z");

        AuditEntryJpaEntity entry = AuditEntryJpaEntity.from(
                new MembershipPlanCoverageChanged(
                        planId,
                        MembershipPlanBranchCoverageScope.SELECTED_BRANCHES,
                        3,
                        7,
                        actorId,
                        "coverage-admin",
                        occurredAt));

        assertThat(entry.actionCode()).isEqualTo(
                "MEMBERSHIP_PLAN_BRANCH_COVERAGE_CHANGED");
        assertThat(entry.resourceType()).isEqualTo("MEMBERSHIP_PLAN");
        assertThat(entry.resourceId()).isEqualTo(planId);
        assertThat(entry.actorUserId()).isEqualTo(actorId);
        assertThat(entry.actorIdentifierSnapshot()).isEqualTo("coverage-admin");
        assertThat(entry.occurredAt()).isEqualTo(occurredAt);
        assertThat(entry.metadata())
                .containsEntry("coverageScope", "SELECTED_BRANCHES")
                .containsEntry("coveredBranchCount", 3)
                .containsEntry("sourcePlanVersion", 7L);
        assertThat(entry.metadata().keySet()).containsExactlyInAnyOrder(
                "coverageScope", "coveredBranchCount", "sourcePlanVersion");
        assertThat(entry.metadata().toString())
                .doesNotContain("branchIds", planId.toString(), "token", "secret");
    }
}
