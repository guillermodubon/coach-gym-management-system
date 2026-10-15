package io.github.guillermodubon.coachgym.organization;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OrganizationLifecyclePolicyTest {

    private final OrganizationBranchLifecyclePolicy policy =
            new OrganizationBranchLifecyclePolicy();

    @Test
    void canonicalOrganizationCannotBeDeactivated() {
        assertThatThrownBy(() -> policy.requireOrganizationTransition(
                OrganizationStatus.ACTIVE,
                OrganizationStatus.INACTIVE,
                true))
                .isInstanceOf(OrganizationStateConflictException.class);

        assertThatCode(() -> policy.requireOrganizationTransition(
                OrganizationStatus.INACTIVE,
                OrganizationStatus.ACTIVE,
                true))
                .doesNotThrowAnyException();
    }

    @Test
    void organizationNoOpIsAStateConflict() {
        assertThatThrownBy(() -> policy.requireOrganizationTransition(
                OrganizationStatus.ACTIVE,
                OrganizationStatus.ACTIVE,
                true))
                .isInstanceOf(OrganizationStateConflictException.class);
    }

    @Test
    void initialBranchCannotBeDeactivatedWhileItIsTheOnlyActiveBranch() {
        assertThatThrownBy(() -> policy.requireBranchTransition(
                GymBranchStatus.ACTIVE,
                GymBranchStatus.INACTIVE,
                true,
                false))
                .isInstanceOf(GymBranchStateConflictException.class);

        assertThatCode(() -> policy.requireBranchTransition(
                GymBranchStatus.ACTIVE,
                GymBranchStatus.INACTIVE,
                true,
                true))
                .doesNotThrowAnyException();
    }

    @Test
    void branchCanReactivateButCannotRepeatTheSameState() {
        assertThatCode(() -> policy.requireBranchTransition(
                GymBranchStatus.INACTIVE,
                GymBranchStatus.ACTIVE,
                false,
                false))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> policy.requireBranchTransition(
                GymBranchStatus.INACTIVE,
                GymBranchStatus.INACTIVE,
                false,
                false))
                .isInstanceOf(GymBranchStateConflictException.class);
    }
}
