package io.github.guillermodubon.coachgym.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembershipPlanBranchCoveragePolicyTest {

    private static final UUID BRANCH_ONE = UUID.fromString(
            "00000000-0000-0000-0000-000000000001");
    private static final UUID BRANCH_TWO = UUID.fromString(
            "00000000-0000-0000-0000-000000000002");

    @Test
    void validatesPlanScopeCardinalityAndNormalizesExplicitBranches() {
        assertThat(MembershipPlanBranchCoveragePolicy.normalizePlanDefinition(
                MembershipPlanBranchCoverageScope.SINGLE_BRANCH,
                Set.of(BRANCH_ONE)))
                .containsExactly(BRANCH_ONE);
        assertThat(MembershipPlanBranchCoveragePolicy.normalizePlanDefinition(
                MembershipPlanBranchCoverageScope.SELECTED_BRANCHES,
                Set.of(BRANCH_TWO, BRANCH_ONE)))
                .containsExactly(BRANCH_ONE, BRANCH_TWO);
        assertThat(MembershipPlanBranchCoveragePolicy.normalizePlanDefinition(
                MembershipPlanBranchCoverageScope.ALL_BRANCHES,
                Set.of())).isEmpty();
    }

    @Test
    void rejectsPlanScopesWithInvalidCardinality() {
        assertThatThrownBy(() ->
                MembershipPlanBranchCoveragePolicy.normalizePlanDefinition(
                        MembershipPlanBranchCoverageScope.SINGLE_BRANCH,
                        Set.of()))
                .isInstanceOf(MembershipPlanBranchCoverageValidationException.class);
        assertThatThrownBy(() ->
                MembershipPlanBranchCoveragePolicy.normalizePlanDefinition(
                        MembershipPlanBranchCoverageScope.SELECTED_BRANCHES,
                        Set.of(BRANCH_ONE)))
                .isInstanceOf(MembershipPlanBranchCoverageValidationException.class);
        assertThatThrownBy(() ->
                MembershipPlanBranchCoveragePolicy.normalizePlanDefinition(
                        MembershipPlanBranchCoverageScope.ALL_BRANCHES,
                        Set.of(BRANCH_ONE)))
                .isInstanceOf(MembershipPlanBranchCoverageValidationException.class);
    }

    @Test
    void capturesAllBranchesAsAFiniteNonEmptyPeriodSnapshot() {
        assertThat(MembershipPlanBranchCoveragePolicy.normalizePeriodSnapshot(
                MembershipPlanBranchCoverageScope.ALL_BRANCHES,
                Set.of(BRANCH_TWO, BRANCH_ONE)))
                .containsExactly(BRANCH_ONE, BRANCH_TWO);
        assertThat(MembershipPlanBranchCoveragePolicy.normalizePeriodSnapshot(
                MembershipPlanBranchCoverageScope.ALL_BRANCHES,
                Set.of(BRANCH_ONE)))
                .containsExactly(BRANCH_ONE);
    }

    @Test
    void rejectsEmptyOrInvalidPeriodSnapshots() {
        assertThatThrownBy(() ->
                MembershipPlanBranchCoveragePolicy.normalizePeriodSnapshot(
                        MembershipPlanBranchCoverageScope.ALL_BRANCHES,
                        Set.of()))
                .isInstanceOf(MembershipPlanBranchCoverageValidationException.class);
        assertThatThrownBy(() ->
                MembershipPlanBranchCoveragePolicy.normalizePeriodSnapshot(
                        MembershipPlanBranchCoverageScope.SELECTED_BRANCHES,
                        Set.of(BRANCH_ONE)))
                .isInstanceOf(MembershipPlanBranchCoverageValidationException.class);
    }

    @Test
    void rejectsNullScopeSetAndBranchIdentifiers() {
        assertThatThrownBy(() ->
                MembershipPlanBranchCoveragePolicy.normalizePlanDefinition(
                        null, Set.of()))
                .isInstanceOf(MembershipPlanBranchCoverageValidationException.class);
        assertThatThrownBy(() ->
                MembershipPlanBranchCoveragePolicy.normalizePlanDefinition(
                        MembershipPlanBranchCoverageScope.ALL_BRANCHES, null))
                .isInstanceOf(MembershipPlanBranchCoverageValidationException.class);

        Set<UUID> withNull = new HashSet<>();
        withNull.add(null);
        assertThatThrownBy(() ->
                MembershipPlanBranchCoveragePolicy.normalizePlanDefinition(
                        MembershipPlanBranchCoverageScope.SINGLE_BRANCH,
                        withNull))
                .isInstanceOf(MembershipPlanBranchCoverageValidationException.class);
    }
}
