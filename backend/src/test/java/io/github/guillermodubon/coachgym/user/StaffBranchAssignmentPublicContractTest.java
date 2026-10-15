package io.github.guillermodubon.coachgym.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffBranchAssignmentPublicContractTest {

    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID BRANCH_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID ORGANIZATION_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Test
    void publicEnumsAndCommandsExposeTheSmallBranchAssignmentVocabulary() {
        assertThat(StaffScopeType.values()).containsExactly(
                StaffScopeType.ORGANIZATION, StaffScopeType.BRANCH);
        assertThat(StaffBranchAssignmentStatus.values()).containsExactly(
                StaffBranchAssignmentStatus.ACTIVE, StaffBranchAssignmentStatus.ENDED);

        assertThat(recordComponentNames(AssignStaffToBranchCommand.class))
                .containsExactlyInAnyOrder("targetUserId", "branchId", "reason");
        assertThat(recordComponentNames(EndStaffBranchAssignmentCommand.class))
                .containsExactlyInAnyOrder("assignmentId", "reason", "expectedVersion");
        assertThat(recordComponentNames(ChangeStaffScopeCommand.class))
                .containsExactlyInAnyOrder("targetUserId", "requestedScope", "reason", "expectedVersion");
        assertThat(recordComponentNames(SelectActiveBranchCommand.class))
                .containsExactly("branchId");
    }

    @Test
    void commandValuesAreNormalizedAndRejectInvalidInput() {
        assertThat(new AssignStaffToBranchCommand(USER_ID, BRANCH_ID, "  initial assignment ").reason())
                .isEqualTo("initial assignment");
        assertThat(new EndStaffBranchAssignmentCommand(BRANCH_ID, "  ended ", 4).reason())
                .isEqualTo("ended");
        assertThat(new ChangeStaffScopeCommand(
                USER_ID, StaffScopeType.BRANCH, "  reassigned ", 2).reason())
                .isEqualTo("reassigned");

        assertThatThrownBy(() -> new AssignStaffToBranchCommand(USER_ID, BRANCH_ID, " "))
                .isInstanceOf(StaffBranchAssignmentValidationException.class);
        assertThatThrownBy(() -> new EndStaffBranchAssignmentCommand(BRANCH_ID, "reason", -1))
                .isInstanceOf(StaffBranchAssignmentValidationException.class);
        assertThatThrownBy(() -> new ChangeStaffScopeCommand(
                USER_ID, StaffScopeType.BRANCH, "reason", -1))
                .isInstanceOf(StaffBranchAssignmentValidationException.class);
    }

    @Test
    void publicRecordsAreImmutableAndDoNotLeakFrameworkOrSensitiveImplementationTypes() {
        AuthorizedBranchSummary branch = new AuthorizedBranchSummary(
                BRANCH_ID, ORGANIZATION_ID, "MAIN", "Main branch", "America/El_Salvador", true);
        StaffBranchContext context = new StaffBranchContext(
                ORGANIZATION_ID, StaffScopeType.BRANCH, BRANCH_ID, List.of(branch));
        StaffScopeDetails scope = new StaffScopeDetails(
                USER_ID, Set.of(RoleCode.RECEPTIONIST), StaffScopeType.BRANCH,
                java.time.Instant.parse("2026-09-21T12:00:00Z"), null, 0);

        assertThat(context.availableBranches()).isUnmodifiable();
        assertThat(scope.roles()).isUnmodifiable();
        assertThat(recordComponentNames(StaffBranchContext.class))
                .containsExactlyInAnyOrder(
                        "organizationId", "scopeType", "activeBranchId", "availableBranches");
        assertThat(recordComponentNames(AuthorizedBranchSummary.class))
                .doesNotContain("email", "phone", "address", "contactDetails", "status");

        for (Class<?> type : List.of(
                StaffAuthorizationContext.class,
                StaffScopeDetails.class,
                StaffBranchAssignmentDetails.class,
                StaffBranchAssignmentPage.class,
                AuthorizedBranchSummary.class,
                StaffBranchContext.class,
                AssignStaffToBranchCommand.class,
                EndStaffBranchAssignmentCommand.class,
                ChangeStaffScopeCommand.class,
                SelectActiveBranchCommand.class)) {
            assertThat(type.isRecord()).isTrue();
            Arrays.stream(type.getDeclaredFields()).forEach(field ->
                    assertThat(field.getType().getName())
                            .doesNotContain("org.springframework", "jakarta.",
                                    ".infrastructure", "jakarta.servlet", "javax.servlet",
                                    "password", "session"));
        }
    }

    @Test
    void portsRemainTechnologyNeutralAndInThePublicUserPackage() {
        for (Class<?> type : List.of(
                StaffScopeQuery.class,
                StaffBranchAssignmentQuery.class,
                AuthorizedBranchQuery.class,
                ActiveBranchContextResolver.class,
                ActiveBranchContextManager.class)) {
            assertThat(type.getPackageName()).isEqualTo("io.github.guillermodubon.coachgym.user");
            Arrays.stream(type.getDeclaredMethods()).forEach(method -> {
                assertThat(method.getReturnType().getName())
                        .doesNotContain("org.springframework", "jakarta.", ".infrastructure");
                Arrays.stream(method.getParameterTypes()).forEach(parameter ->
                        assertThat(parameter.getName())
                                .doesNotContain("org.springframework", "jakarta.", ".infrastructure"));
            });
        }
    }

    private static Set<String> recordComponentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
