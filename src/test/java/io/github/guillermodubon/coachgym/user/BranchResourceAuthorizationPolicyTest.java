package io.github.guillermodubon.coachgym.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BranchResourceAuthorizationPolicyTest {

    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ORGANIZATION_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_ORGANIZATION_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID BRANCH_A = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID BRANCH_B = UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID RESOURCE_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");

    @Test
    void activeContextIsRequiredAndMustStillBeAuthorized() {
        BranchOperationContext noSelection = context(StaffScopeType.BRANCH, null, BRANCH_A);
        BranchOperationContext staleSelection = context(StaffScopeType.BRANCH, BRANCH_B, BRANCH_A);

        assertThatThrownBy(() -> BranchResourceAuthorizationPolicy.requireActiveBranch(noSelection))
                .isInstanceOf(ActiveBranchContextUnavailableException.class);
        assertThatThrownBy(() -> BranchResourceAuthorizationPolicy.requireActiveBranch(staleSelection))
                .isInstanceOf(ActiveBranchContextUnavailableException.class);
    }

    @Test
    void branchScopedOperationsAreRestrictedToTheSelectedAuthorizedBranch() {
        BranchOperationContext branchContext = context(StaffScopeType.BRANCH, BRANCH_A, BRANCH_A, BRANCH_B);

        BranchResourceAuthorizationPolicy.requireActiveResourceAccess(
                branchContext, resource(BRANCH_A));
        assertThatThrownBy(() -> BranchResourceAuthorizationPolicy.requireActiveResourceAccess(
                branchContext, resource(BRANCH_B)))
                .isInstanceOf(BranchResourceAuthorizationException.class);
    }

    @Test
    void organizationAdministratorsMayAddressAnExplicitAuthorizedActiveBranch() {
        BranchOperationContext organizationContext =
                context(StaffScopeType.ORGANIZATION, null, BRANCH_A, BRANCH_B);

        BranchResourceAuthorizationPolicy.requireOrganizationResourceAccess(
                organizationContext, resource(BRANCH_B));
        assertThatThrownBy(() -> BranchResourceAuthorizationPolicy.requireOrganizationResourceAccess(
                organizationContext, resourceInOtherOrganization(BRANCH_B)))
                .isInstanceOf(BranchResourceAuthorizationException.class);
        assertThatThrownBy(() -> BranchResourceAuthorizationPolicy.requireOrganizationResourceAccess(
                context(StaffScopeType.BRANCH, BRANCH_A, BRANCH_A), resource(BRANCH_A)))
                .isInstanceOf(BranchResourceAuthorizationException.class);
    }

    @Test
    void listQueriesRequireAnActiveOrExplicitlyAuthorizedBranch() {
        BranchOperationContext branchContext =
                context(StaffScopeType.BRANCH, BRANCH_A, BRANCH_A, BRANCH_B);
        BranchOperationContext organizationContext =
                context(StaffScopeType.ORGANIZATION, null, BRANCH_A, BRANCH_B);

        assertThat(BranchResourceAuthorizationPolicy.requireListBranch(
                branchContext, null)).isEqualTo(BRANCH_A);
        assertThat(BranchResourceAuthorizationPolicy.requireListBranch(
                branchContext, BRANCH_A)).isEqualTo(BRANCH_A);
        assertThatThrownBy(() -> BranchResourceAuthorizationPolicy.requireListBranch(
                branchContext, BRANCH_B))
                .isInstanceOf(BranchResourceAuthorizationException.class);
        assertThat(BranchResourceAuthorizationPolicy.requireListBranch(
                organizationContext, BRANCH_B)).isEqualTo(BRANCH_B);
        assertThatThrownBy(() -> BranchResourceAuthorizationPolicy.requireListBranch(
                organizationContext, null))
                .isInstanceOf(ActiveBranchContextUnavailableException.class);
        assertThatThrownBy(() -> BranchResourceAuthorizationPolicy.requireListBranch(
                organizationContext, UUID.randomUUID()))
                .isInstanceOf(BranchResourceAuthorizationException.class);
    }

    @Test
    void creationUsesServerContextAndRejectsConflictingClientBranch() {
        BranchOperationContext context = context(StaffScopeType.ORGANIZATION, BRANCH_A, BRANCH_A, BRANCH_B);

        assertThat(BranchResourceAuthorizationPolicy.requireCreationBranch(context, null))
                .isEqualTo(BRANCH_A);
        assertThatThrownBy(() -> BranchResourceAuthorizationPolicy.requireCreationBranch(context, BRANCH_B))
                .isInstanceOf(BranchResourceMismatchException.class);
    }

    @Test
    void publicContractsRemainFrameworkFreeAndDenialsDoNotRevealIdentifiers() {
        assertThat(BranchOperationContext.class.getPackageName())
                .isEqualTo("io.github.guillermodubon.coachgym.user");
        assertThat(BranchOwnedResourceReference.class.isRecord()).isTrue();
        assertThat(recordComponentNames(BranchOperationContext.class))
                .containsExactlyInAnyOrder(
                        "userId", "organizationId", "scopeType",
                        "activeBranchId", "authorizedActiveBranchIds");
        assertThat(recordComponentNames(BranchOwnedResourceReference.class))
                .containsExactlyInAnyOrder("resourceId", "organizationId", "branchId");
        assertThat(new BranchOperationContext(
                USER_ID, ORGANIZATION_ID, StaffScopeType.BRANCH, BRANCH_A, Set.of(BRANCH_A))
                .authorizedActiveBranchIds()).isUnmodifiable();

        for (Class<?> type : List.of(
                BranchOperationContext.class,
                BranchOwnedResourceReference.class)) {
            Arrays.stream(type.getDeclaredFields()).forEach(field ->
                    assertThat(field.getType().getName())
                            .doesNotContain("org.springframework", "jakarta.",
                                    ".infrastructure", "jakarta.servlet", "javax.servlet",
                                    "password", "session"));
        }

        BranchResourceAuthorizationException exception =
                new BranchResourceAuthorizationException();
        assertThat(exception.getMessage())
                .doesNotContain(BRANCH_A.toString())
                .doesNotContain(RESOURCE_ID.toString());
    }

    private static Set<String> recordComponentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static BranchOperationContext context(
            StaffScopeType scopeType,
            UUID activeBranchId,
            UUID... authorizedBranchIds) {
        return new BranchOperationContext(
                USER_ID,
                ORGANIZATION_ID,
                scopeType,
                activeBranchId,
                Set.of(authorizedBranchIds));
    }

    private static BranchOwnedResourceReference resource(UUID branchId) {
        return new BranchOwnedResourceReference(RESOURCE_ID, ORGANIZATION_ID, branchId);
    }

    private static BranchOwnedResourceReference resourceInOtherOrganization(UUID branchId) {
        return new BranchOwnedResourceReference(RESOURCE_ID, OTHER_ORGANIZATION_ID, branchId);
    }
}
