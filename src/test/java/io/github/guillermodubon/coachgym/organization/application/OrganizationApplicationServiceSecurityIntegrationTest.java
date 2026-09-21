package io.github.guillermodubon.coachgym.organization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import io.github.guillermodubon.coachgym.organization.CreateGymBranchCommand;
import io.github.guillermodubon.coachgym.organization.UpdateOrganizationCommand;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

class OrganizationApplicationServiceSecurityIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    @Autowired
    private OrganizationApplicationService organizationService;

    @Autowired
    private GymBranchApplicationService branchService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanReadTheFullOrganizationAndBranchCatalog() {
        assertThat(organizationService.findCanonical().code()).isEqualTo("COACH_GYM");
        assertThat(organizationService.findCanonicalSummary().code()).isEqualTo("COACH_GYM");
        assertThat(branchService.findAll(GymBranchSearchQuery.defaults()).items())
                .isNotEmpty();
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void receptionistCanReadSafeOrganizationSummaryAndBranchesButCannotMutate() {
        assertThat(organizationService.findCanonicalSummary().code()).isEqualTo("COACH_GYM");
        assertThat(branchService.findAll(GymBranchSearchQuery.defaults()).items())
                .isNotEmpty();

        assertThatThrownBy(organizationService::findCanonical)
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> organizationService.update(
                new UpdateOrganizationCommand(
                        "Coach Gym", "Coach Gym", null, null,
                        "America/El_Salvador", "USD", 0),
                new AuthenticatedActor(UUID.randomUUID(), RECEPTIONIST_USERNAME)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> branchService.create(
                new CreateGymBranchCommand(
                        "DENIED", "Denied Branch", null, null, null, null,
                        null, "SV", null, null, null),
                new AuthenticatedActor(UUID.randomUUID(), RECEPTIONIST_USERNAME)))
                .isInstanceOf(AccessDeniedException.class);
    }
}
