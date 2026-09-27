package io.github.guillermodubon.coachgym.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembershipPlanSaleCoverageTest {

    @Test
    void normalizesExactFiniteCoverageForEveryScope() {
        UUID planId = UUID.randomUUID();
        UUID branchA = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID branchB = UUID.fromString("00000000-0000-0000-0000-000000000002");

        MembershipPlanSaleCoverage single = new MembershipPlanSaleCoverage(
                planId,
                MembershipPlanBranchCoverageScope.SINGLE_BRANCH,
                Set.of(branchA),
                0);
        MembershipPlanSaleCoverage selected = new MembershipPlanSaleCoverage(
                planId,
                MembershipPlanBranchCoverageScope.SELECTED_BRANCHES,
                Set.of(branchB, branchA),
                2);
        MembershipPlanSaleCoverage all = new MembershipPlanSaleCoverage(
                planId,
                MembershipPlanBranchCoverageScope.ALL_BRANCHES,
                Set.of(branchB, branchA),
                3);

        assertThat(single.coveredBranchIds()).containsExactly(branchA);
        assertThat(selected.coveredBranchIds()).containsExactly(branchA, branchB);
        assertThat(all.coveredBranchIds()).containsExactly(branchA, branchB);
        assertThat(all.includes(branchB)).isTrue();
        assertThat(all.includes(null)).isFalse();
        assertThatThrownBy(() -> all.coveredBranchIds().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsEmptyOrInvalidFiniteCoverage() {
        UUID planId = UUID.randomUUID();

        assertThatThrownBy(() -> new MembershipPlanSaleCoverage(
                planId,
                MembershipPlanBranchCoverageScope.SINGLE_BRANCH,
                Set.of(),
                0)).isInstanceOf(MembershipPlanBranchCoverageValidationException.class);
        assertThatThrownBy(() -> new MembershipPlanSaleCoverage(
                planId,
                MembershipPlanBranchCoverageScope.SELECTED_BRANCHES,
                Set.of(UUID.randomUUID()),
                0)).isInstanceOf(MembershipPlanBranchCoverageValidationException.class);
        assertThatThrownBy(() -> new MembershipPlanSaleCoverage(
                planId,
                MembershipPlanBranchCoverageScope.ALL_BRANCHES,
                Set.of(),
                0)).isInstanceOf(MembershipPlanBranchCoverageValidationException.class);
        assertThatThrownBy(() -> new MembershipPlanSaleCoverage(
                planId,
                MembershipPlanBranchCoverageScope.ALL_BRANCHES,
                Set.of(UUID.randomUUID()),
                -1)).isInstanceOf(MembershipPlanBranchCoverageValidationException.class);
    }
}
