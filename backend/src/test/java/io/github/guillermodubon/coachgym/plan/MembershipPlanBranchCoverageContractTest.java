package io.github.guillermodubon.coachgym.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembershipPlanBranchCoverageContractTest {

    private static final UUID PLAN_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");
    private static final UUID BRANCH_ONE = UUID.fromString(
            "20000000-0000-0000-0000-000000000001");
    private static final UUID BRANCH_TWO = UUID.fromString(
            "20000000-0000-0000-0000-000000000002");

    @Test
    void coverageDetailsAndUpdateCommandDefensivelyCopyBranchSets() {
        Set<UUID> mutableBranches = new HashSet<>(Set.of(BRANCH_ONE, BRANCH_TWO));
        MembershipPlanBranchCoverageDetails details =
                new MembershipPlanBranchCoverageDetails(
                        PLAN_ID,
                        MembershipPlanBranchCoverageScope.SELECTED_BRANCHES,
                        mutableBranches,
                        3);
        UpdateMembershipPlanBranchCoverageCommand command =
                new UpdateMembershipPlanBranchCoverageCommand(
                        MembershipPlanBranchCoverageScope.SELECTED_BRANCHES,
                        mutableBranches,
                        3);
        mutableBranches.clear();

        assertThat(details.branchIds()).containsExactly(BRANCH_ONE, BRANCH_TWO);
        assertThat(command.branchIds()).containsExactly(BRANCH_ONE, BRANCH_TWO);
        assertThatThrownBy(() -> details.branchIds().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> command.branchIds().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void contractsHaveExplicitAllowlistedComponentsAndNoFrameworkTypes() {
        assertThat(Arrays.stream(
                MembershipPlanBranchCoverageDetails.class.getRecordComponents())
                .map(component -> component.getName()))
                .containsExactly("planId", "scope", "branchIds", "version");
        assertThat(Arrays.stream(
                UpdateMembershipPlanBranchCoverageCommand.class.getRecordComponents())
                .map(component -> component.getName()))
                .containsExactly("scope", "branchIds", "expectedVersion");
        assertThat(Arrays.stream(MembershipPlanCoverageChanged.class.getRecordComponents())
                .map(component -> component.getName()))
                .containsExactly(
                        "planId", "scope", "explicitBranchCount", "planVersion",
                        "actorUserId", "actorIdentifier", "occurredAt");
        assertTechnologyNeutral(MembershipPlanBranchCoverageDetails.class);
        assertTechnologyNeutral(UpdateMembershipPlanBranchCoverageCommand.class);
        assertTechnologyNeutral(MembershipPlanCoverageChanged.class);
        assertThat(Modifier.isPublic(
                MembershipPlanBranchCoverageScope.class.getModifiers())).isTrue();
        assertThat(MembershipPlanBranchCoverageScope.values())
                .containsExactly(
                        MembershipPlanBranchCoverageScope.SINGLE_BRANCH,
                        MembershipPlanBranchCoverageScope.SELECTED_BRANCHES,
                        MembershipPlanBranchCoverageScope.ALL_BRANCHES);

        assertThat(MembershipPlanBranchCoverageQuery.class.isAnnotationPresent(
                FunctionalInterface.class)).isTrue();
        assertThat(MembershipPlanBranchCoverageQuery.class.getMethods())
                .filteredOn(method -> method.getName().equals("findCoverage"))
                .singleElement()
                .satisfies(method -> {
                    assertThat(method.getParameterTypes())
                            .containsExactly(UUID.class);
                    assertThat(method.getReturnType()).isEqualTo(Optional.class);
                });
        assertThat(MembershipPlanBranchEligibilityQuery.class.isAnnotationPresent(
                FunctionalInterface.class)).isTrue();
        assertThat(MembershipPlanBranchEligibilityQuery.class.getMethods())
                .filteredOn(method -> method.getName().equals("isValidAtBranch"))
                .singleElement()
                .satisfies(method -> {
                    assertThat(method.getParameterTypes())
                            .containsExactly(UUID.class, UUID.class);
                    assertThat(method.getReturnType()).isEqualTo(boolean.class);
                });
    }

    @Test
    void rejectsInvalidPlanIdentifiersAndNegativeVersions() {
        assertThatThrownBy(() -> new MembershipPlanBranchCoverageDetails(
                null,
                MembershipPlanBranchCoverageScope.SINGLE_BRANCH,
                Set.of(BRANCH_ONE),
                0))
                .isInstanceOf(MembershipPlanBranchCoverageValidationException.class);
        assertThatThrownBy(() -> new MembershipPlanBranchCoverageDetails(
                PLAN_ID,
                MembershipPlanBranchCoverageScope.SINGLE_BRANCH,
                Set.of(BRANCH_ONE),
                -1))
                .isInstanceOf(MembershipPlanBranchCoverageValidationException.class);
        assertThatThrownBy(() -> new UpdateMembershipPlanBranchCoverageCommand(
                MembershipPlanBranchCoverageScope.SINGLE_BRANCH,
                Set.of(BRANCH_ONE),
                -1))
                .isInstanceOf(MembershipPlanBranchCoverageValidationException.class);
    }

    private static void assertTechnologyNeutral(Class<?> contract) {
        assertThat(Arrays.stream(contract.getRecordComponents())
                .flatMap(component -> Arrays.stream(component.getType().getName().split("\\."))))
                .noneMatch(name -> name.equals("springframework")
                        || name.equals("persistence")
                        || name.equals("servlet"));
    }
}
