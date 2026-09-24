package io.github.guillermodubon.coachgym.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.plan.MembershipPlanBranchCoverageScope;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembershipPeriodBranchCoverageContractTest {

    private static final UUID PERIOD_ID = UUID.fromString(
            "30000000-0000-0000-0000-000000000001");
    private static final UUID BRANCH_ONE = UUID.fromString(
            "40000000-0000-0000-0000-000000000001");
    private static final UUID BRANCH_TWO = UUID.fromString(
            "40000000-0000-0000-0000-000000000002");

    @Test
    void allBranchesPlanIsReturnedAsAnImmutableFinitePeriodSnapshot() {
        Set<UUID> mutableBranches = new HashSet<>(Set.of(BRANCH_TWO, BRANCH_ONE));
        MembershipPeriodBranchCoverageDetails details =
                new MembershipPeriodBranchCoverageDetails(
                        PERIOD_ID,
                        MembershipPlanBranchCoverageScope.ALL_BRANCHES,
                        mutableBranches,
                        Instant.parse("2026-09-23T12:00:00Z"),
                        4);
        mutableBranches.clear();

        assertThat(details.coveredBranchIds()).containsExactly(BRANCH_ONE, BRANCH_TWO);
        assertThatThrownBy(() -> details.coveredBranchIds().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void snapshotRequiresIdentityCaptureTimeAndNonNegativeSourceVersion() {
        assertThatThrownBy(() -> new MembershipPeriodBranchCoverageDetails(
                null,
                MembershipPlanBranchCoverageScope.SINGLE_BRANCH,
                Set.of(BRANCH_ONE),
                Instant.EPOCH,
                0)).isInstanceOf(MembershipPeriodBranchCoverageValidationException.class);
        assertThatThrownBy(() -> new MembershipPeriodBranchCoverageDetails(
                PERIOD_ID,
                MembershipPlanBranchCoverageScope.SINGLE_BRANCH,
                Set.of(BRANCH_ONE),
                null,
                0)).isInstanceOf(MembershipPeriodBranchCoverageValidationException.class);
        assertThatThrownBy(() -> new MembershipPeriodBranchCoverageDetails(
                PERIOD_ID,
                MembershipPlanBranchCoverageScope.SINGLE_BRANCH,
                Set.of(BRANCH_ONE),
                Instant.EPOCH,
                -1)).isInstanceOf(MembershipPeriodBranchCoverageValidationException.class);
    }

    @Test
    void snapshotAppliesScopeCardinalityWithoutLeakingPlanPolicyExceptions() {
        assertThatThrownBy(() -> new MembershipPeriodBranchCoverageDetails(
                PERIOD_ID,
                MembershipPlanBranchCoverageScope.ALL_BRANCHES,
                Set.of(),
                Instant.EPOCH,
                0)).isInstanceOf(MembershipPeriodBranchCoverageValidationException.class);
        assertThatThrownBy(() -> new MembershipPeriodBranchCoverageDetails(
                PERIOD_ID,
                MembershipPlanBranchCoverageScope.SELECTED_BRANCHES,
                Set.of(BRANCH_ONE),
                Instant.EPOCH,
                0)).isInstanceOf(MembershipPeriodBranchCoverageValidationException.class);
    }

    @Test
    void queryBoundaryReturnsOnlyAContainmentDecision() throws Exception {
        Method method = MembershipPeriodBranchCoverageQuery.class.getMethod(
                "coversBranch", UUID.class, UUID.class);

        assertThat(MembershipPeriodBranchCoverageQuery.class.isAnnotationPresent(
                FunctionalInterface.class)).isTrue();
        assertThat(method.getReturnType()).isEqualTo(boolean.class);
        assertThat(Arrays.asList(method.getParameterTypes()))
                .containsExactly(UUID.class, UUID.class);
    }
}
