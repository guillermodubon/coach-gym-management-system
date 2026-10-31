package io.github.guillermodubon.coachgym.organization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.core.context.SecurityContextHolder.clearContext;
import static org.springframework.security.core.context.SecurityContextHolder.getContext;

import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import io.github.guillermodubon.coachgym.organization.CreateGymBranchCommand;
import io.github.guillermodubon.coachgym.organization.ChangeOrganizationStatusCommand;
import io.github.guillermodubon.coachgym.organization.OrganizationStatus;
import io.github.guillermodubon.coachgym.organization.UpdateOrganizationCommand;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.AuthenticatedActorProvider;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;

class OrganizationApplicationServiceSecurityIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    @Autowired
    private OrganizationApplicationService organizationService;

    @Autowired
    private GymBranchApplicationService branchService;

    @AfterEach
    void clearThreadAuthentication() {
        clearContext();
    }

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

    @Test
    void organizationStatusMethodUsesPersistedScopeAndKeepsItsHttpBoundaryAbsent() {
        UUID branchAdminId = provisionUser(
                "organization-status-branch-admin",
                "organization-status-branch-admin@example.test",
                "Branch-admin-strong-password",
                "ADMIN");
        jdbcTemplate.update("""
                update gym.staff_scopes
                set scope_type = 'BRANCH', version = version + 1
                where user_id = ? and scope_type = 'ORGANIZATION'
                """, branchAdminId);

        AuthenticatedActor organizationActor =
                new AuthenticatedActor(adminId, ADMIN_USERNAME);
        authenticateAsAdmin(organizationActor);
        var current = organizationService.findCanonical();
        var deactivation = new ChangeOrganizationStatusCommand(
                OrganizationStatus.INACTIVE, "Integration authorization check", current.version());
        int auditCountBefore = organizationAuditCount(current.id());

        assertThatThrownBy(() -> organizationService.changeStatus(deactivation, organizationActor))
                .isInstanceOf(io.github.guillermodubon.coachgym.organization.OrganizationStateConflictException.class);

        AuthenticatedActor branchActor = new AuthenticatedActor(
                branchAdminId, "organization-status-branch-admin");
        authenticateAsAdmin(branchActor);
        assertThatThrownBy(() -> organizationService.changeStatus(deactivation, branchActor))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(organizationService.findCanonical()).isEqualTo(current);
        assertThat(organizationAuditCount(current.id())).isEqualTo(auditCountBefore);
    }

    private int organizationAuditCount(UUID organizationId) {
        return jdbcTemplate.queryForObject("""
                select count(*) from gym.audit_entries
                where resource_type = 'ORGANIZATION' and resource_id = ?
                """, Integer.class, organizationId);
    }

    private static void authenticateAsAdmin(AuthenticatedActor actor) {
        AuthenticatedActorProvider principal = () -> actor;
        getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
}
