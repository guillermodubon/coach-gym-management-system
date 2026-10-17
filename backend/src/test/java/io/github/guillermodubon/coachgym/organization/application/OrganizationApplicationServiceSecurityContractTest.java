package io.github.guillermodubon.coachgym.organization.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.organization.ChangeGymBranchStatusCommand;
import io.github.guillermodubon.coachgym.organization.ChangeOrganizationStatusCommand;
import io.github.guillermodubon.coachgym.organization.CreateGymBranchCommand;
import io.github.guillermodubon.coachgym.organization.UpdateGymBranchCommand;
import io.github.guillermodubon.coachgym.organization.UpdateOrganizationCommand;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

class OrganizationApplicationServiceSecurityContractTest {

    @Test
    void organizationReadPolicySeparatesAdministrativeDetailsFromSummary() throws Exception {
        assertAuthorization(
                OrganizationApplicationService.class,
                "findCanonical",
                "hasRole('ADMIN')");
        assertAuthorization(
                OrganizationApplicationService.class,
                "findCanonicalSummary",
                "hasAnyRole('ADMIN', 'RECEPTIONIST')");
        assertAuthorization(
                OrganizationApplicationService.class,
                "update",
                "hasRole('ADMIN')",
                UpdateOrganizationCommand.class,
                AuthenticatedActor.class);
        assertAuthorization(
                OrganizationApplicationService.class,
                "changeStatus",
                "hasRole('ADMIN')",
                ChangeOrganizationStatusCommand.class,
                AuthenticatedActor.class);
    }

    @Test
    void branchReadsAllowBothStaffRolesButMutationsRequireAdmin() throws Exception {
        assertAuthorization(
                GymBranchApplicationService.class,
                "findAll",
                "hasAnyRole('ADMIN', 'RECEPTIONIST')",
                GymBranchSearchQuery.class);
        assertAuthorization(
                GymBranchApplicationService.class,
                "findById",
                "hasAnyRole('ADMIN', 'RECEPTIONIST')",
                java.util.UUID.class);
        assertAuthorization(
                GymBranchApplicationService.class,
                "create",
                "hasRole('ADMIN')",
                CreateGymBranchCommand.class,
                AuthenticatedActor.class);
        assertAuthorization(
                GymBranchApplicationService.class,
                "update",
                "hasRole('ADMIN')",
                java.util.UUID.class,
                UpdateGymBranchCommand.class,
                AuthenticatedActor.class);
        assertAuthorization(
                GymBranchApplicationService.class,
                "changeStatus",
                "hasRole('ADMIN')",
                java.util.UUID.class,
                ChangeGymBranchStatusCommand.class,
                AuthenticatedActor.class);
    }

    @Test
    void serviceMethodsDeclareReadOnlyAndMutationTransactions() throws Exception {
        assertReadOnly(OrganizationApplicationService.class.getMethod("findCanonical"));
        assertReadOnly(OrganizationApplicationService.class.getMethod("findCanonicalSummary"));
        assertReadOnly(GymBranchApplicationService.class.getMethod(
                "findAll", GymBranchSearchQuery.class));
        assertReadOnly(GymBranchApplicationService.class.getMethod(
                "findById", java.util.UUID.class));
        assertWritable(OrganizationApplicationService.class.getMethod(
                "update", UpdateOrganizationCommand.class, AuthenticatedActor.class));
        assertWritable(GymBranchApplicationService.class.getMethod(
                "create", CreateGymBranchCommand.class, AuthenticatedActor.class));
    }

    private static void assertAuthorization(
            Class<?> type,
            String name,
            String expression,
            Class<?>... parameterTypes) throws Exception {
        Method method = type.getMethod(name, parameterTypes);
        assertThat(method.getAnnotation(PreAuthorize.class)).isNotNull();
        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo(expression)
                .doesNotContain("MAINTENANCE");
    }

    private static void assertReadOnly(Method method) {
        assertThat(method.getAnnotation(Transactional.class)).isNotNull();
        assertThat(method.getAnnotation(Transactional.class).readOnly()).isTrue();
    }

    private static void assertWritable(Method method) {
        assertThat(method.getAnnotation(Transactional.class)).isNotNull();
        assertThat(method.getAnnotation(Transactional.class).readOnly()).isFalse();
    }
}
