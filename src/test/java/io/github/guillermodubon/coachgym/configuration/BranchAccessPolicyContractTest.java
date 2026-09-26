package io.github.guillermodubon.coachgym.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BranchAccessPolicyContractTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString(
            "50000000-0000-0000-0000-000000000001");
    private static final UUID BRANCH_ID = UUID.fromString(
            "60000000-0000-0000-0000-000000000001");

    @Test
    void branchOverrideWinsAndInheritUsesOrganizationDefault() {
        assertThat(policy(true, BranchAccessPaymentPolicyMode.INHERIT)
                .requireConfirmedPaymentForAccess()).isTrue();
        assertThat(policy(false, BranchAccessPaymentPolicyMode.INHERIT)
                .requireConfirmedPaymentForAccess()).isFalse();
        assertThat(policy(false, BranchAccessPaymentPolicyMode.REQUIRED)
                .requireConfirmedPaymentForAccess()).isTrue();
        assertThat(policy(true, BranchAccessPaymentPolicyMode.NOT_REQUIRED)
                .requireConfirmedPaymentForAccess()).isFalse();
    }

    @Test
    void branchUpdateCommandIsAllowlistedAndVersioned() {
        assertThat(Arrays.stream(
                UpdateBranchAccessPolicyCommand.class.getRecordComponents())
                .map(component -> component.getName()))
                .containsExactly("branchId", "mode", "expectedVersion");
        assertThat(new UpdateBranchAccessPolicyCommand(
                BRANCH_ID, BranchAccessPaymentPolicyMode.INHERIT, 2).mode())
                .isEqualTo(BranchAccessPaymentPolicyMode.INHERIT);
        assertThatThrownBy(() -> new UpdateBranchAccessPolicyCommand(
                BRANCH_ID, BranchAccessPaymentPolicyMode.REQUIRED, -1))
                .isInstanceOf(AccessPaymentPolicyValidationException.class);
        assertThatThrownBy(() -> new UpdateBranchAccessPolicyCommand(
                null, BranchAccessPaymentPolicyMode.REQUIRED, 0))
                .isInstanceOf(AccessPaymentPolicyValidationException.class);
    }

    @Test
    void effectivePolicyRequiresOrganizationBranchAndExplicitMode() {
        assertThatThrownBy(() -> new EffectiveBranchAccessPolicy(
                null, BRANCH_ID, false, BranchAccessPaymentPolicyMode.INHERIT))
                .isInstanceOf(AccessPaymentPolicyValidationException.class);
        assertThatThrownBy(() -> new EffectiveBranchAccessPolicy(
                ORGANIZATION_ID, null, false, BranchAccessPaymentPolicyMode.INHERIT))
                .isInstanceOf(AccessPaymentPolicyValidationException.class);
        assertThatThrownBy(() -> new EffectiveBranchAccessPolicy(
                ORGANIZATION_ID, BRANCH_ID, false, null))
                .isInstanceOf(AccessPaymentPolicyValidationException.class);
        assertThatThrownBy(() -> new EffectiveBranchAccessPolicy(
                ORGANIZATION_ID,
                BRANCH_ID,
                false,
                BranchAccessPaymentPolicyMode.INHERIT,
                -1))
                .isInstanceOf(AccessPaymentPolicyValidationException.class);
    }

    @Test
    void policyQueryIsAMinimalFrameworkFreePublicBoundary() throws Exception {
        java.lang.reflect.Method method = BranchAccessPolicyQuery.class.getMethod(
                "findForBranch", UUID.class, UUID.class);

        assertThat(BranchAccessPolicyQuery.class.isAnnotationPresent(
                FunctionalInterface.class)).isTrue();
        assertThat(method.getParameterTypes())
                .containsExactly(UUID.class, UUID.class);
        assertThat(method.getReturnType())
                .isEqualTo(EffectiveBranchAccessPolicy.class);
        assertThat(Arrays.stream(
                BranchAccessPolicyQuery.class.getDeclaredMethods())
                .flatMap(declared -> Arrays.stream(declared.getParameterTypes()))
                .map(Class::getName))
                .doesNotContain("org.springframework.security.core.Authentication",
                        "jakarta.servlet.http.HttpServletRequest");
    }

    private static EffectiveBranchAccessPolicy policy(
            boolean organizationDefault,
            BranchAccessPaymentPolicyMode mode) {
        return new EffectiveBranchAccessPolicy(
                ORGANIZATION_ID, BRANCH_ID, organizationDefault, mode);
    }
}
