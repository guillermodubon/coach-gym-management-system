package io.github.guillermodubon.coachgym.organization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.organization.ChangeGymBranchStatusCommand;
import io.github.guillermodubon.coachgym.organization.ChangeOrganizationStatusCommand;
import io.github.guillermodubon.coachgym.organization.CreateGymBranchCommand;
import io.github.guillermodubon.coachgym.organization.GymBranchDetails;
import io.github.guillermodubon.coachgym.organization.GymBranchStatus;
import io.github.guillermodubon.coachgym.organization.OrganizationDetails;
import io.github.guillermodubon.coachgym.organization.OrganizationStatus;
import io.github.guillermodubon.coachgym.organization.UpdateGymBranchCommand;
import io.github.guillermodubon.coachgym.organization.UpdateOrganizationCommand;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.AuthenticatedActorProvider;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffAuthorizationContext;
import io.github.guillermodubon.coachgym.user.StaffScopeQuery;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringJUnitConfig(OrganizationAdministrationMethodSecurityTest.TestConfiguration.class)
class OrganizationAdministrationMethodSecurityTest {

    private static final UUID ACTOR_ID = UUID.fromString("7b0bf7d5-5184-43d2-8f9a-200000000080");
    private static final UUID ORGANIZATION_ID = UUID.fromString("7b0bf7d5-5184-43d2-8f9a-200000000001");
    private static final UUID BRANCH_ID = UUID.fromString("7b0bf7d5-5184-43d2-8f9a-200000000081");
    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");
    private static final AuthenticatedActor ACTOR = new AuthenticatedActor(ACTOR_ID, "organization-admin");
    private static final OrganizationDetails ORGANIZATION = new OrganizationDetails(
            ORGANIZATION_ID, "COACH_GYM", "Coach Gym", "Coach Gym", null, null,
            "America/El_Salvador", "USD", OrganizationStatus.ACTIVE,
            NOW.minusSeconds(60), NOW, 0);
    private static final GymBranchDetails BRANCH = new GymBranchDetails(
            BRANCH_ID, ORGANIZATION_ID, "NORTH", "North Gym", null, null,
            null, null, null, "SV", null, null, "America/El_Salvador",
            GymBranchStatus.ACTIVE, false, NOW.minusSeconds(60), NOW, 0);
    private static final GymBranchDetails INACTIVE_BRANCH = new GymBranchDetails(
            BRANCH_ID, ORGANIZATION_ID, "NORTH", "North Gym", null, null,
            null, null, null, "SV", null, null, "America/El_Salvador",
            GymBranchStatus.INACTIVE, false, NOW.minusSeconds(60), NOW, 1);

    @Autowired private OrganizationApplicationService organizationService;
    @Autowired private GymBranchApplicationService branchService;
    @Autowired private StaffScopeQuery scopeQuery;
    @Autowired private OrganizationQuery organizationQuery;
    @Autowired private OrganizationStore organizationStore;
    @Autowired private GymBranchQuery branchQuery;
    @Autowired private GymBranchStore branchStore;
    @Autowired private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void resetCollaborators() {
        reset(scopeQuery, organizationQuery, organizationStore,
                branchQuery, branchStore, eventPublisher);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allFiveEntrypointsUseCurrentScopeInsideTheirTransaction() {
        authenticate(ACTOR, "ADMIN");
        when(scopeQuery.findAuthorizationContext(ACTOR_ID)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return Optional.of(context(RoleCode.ADMIN, StaffAccountStatus.ACTIVE,
                    StaffScopeType.ORGANIZATION));
        });
        when(organizationQuery.findCanonical()).thenReturn(Optional.of(ORGANIZATION));
        when(organizationStore.update(any(), any())).thenReturn(ORGANIZATION);
        when(organizationStore.changeStatus(any(), anyLong(), any())).thenReturn(ORGANIZATION);
        when(branchStore.create(any(), any())).thenReturn(BRANCH);
        when(branchQuery.findById(BRANCH_ID)).thenReturn(Optional.of(BRANCH));
        when(branchStore.update(any(), any(), any())).thenReturn(BRANCH);
        when(branchStore.changeStatus(any(), any(), any())).thenReturn(INACTIVE_BRANCH);

        mutations(ACTOR, 0).forEach(Runnable::run);

        verify(scopeQuery, times(5)).findAuthorizationContext(ACTOR_ID);
        verify(organizationStore).update(any(), any());
        verify(organizationStore).changeStatus(any(), anyLong(), any());
        verify(branchStore).create(any(), any());
        verify(branchStore).update(any(), any(), any());
        verify(branchStore).changeStatus(any(), any(), any());
    }

    @Test
    void branchAdministratorIsDeniedBeforeAnyResourceLookupEvenForStaleOrMissingTargets() {
        authenticate(ACTOR, "ADMIN");
        when(scopeQuery.findAuthorizationContext(ACTOR_ID)).thenReturn(Optional.of(
                context(RoleCode.ADMIN, StaffAccountStatus.ACTIVE, StaffScopeType.BRANCH)));

        for (Runnable mutation : mutations(ACTOR)) {
            assertThatThrownBy(mutation::run).isInstanceOf(AccessDeniedException.class);
        }

        verify(scopeQuery, times(5)).findAuthorizationContext(ACTOR_ID);
        verifyNoBusinessEffects();
    }

    @Test
    void receptionistIsDeniedByMethodSecurityWithoutReadingScopeOrResources() {
        authenticate(ACTOR, "RECEPTIONIST");

        for (Runnable mutation : mutations(ACTOR)) {
            assertThatThrownBy(mutation::run).isInstanceOf(AccessDeniedException.class);
        }

        verifyNoInteractions(scopeQuery);
        verifyNoBusinessEffects();
    }

    @Test
    void spoofedActorAndGenericPrincipalCannotReachPersistedAuthority() {
        authenticate(ACTOR, "ADMIN");
        for (AuthenticatedActor spoofed : List.of(
                new AuthenticatedActor(UUID.randomUUID(), ACTOR.username()),
                new AuthenticatedActor(ACTOR_ID, "other-user"))) {
            for (Runnable mutation : mutations(spoofed)) {
                assertThatThrownBy(mutation::run).isInstanceOf(AccessDeniedException.class);
            }
        }
        authenticate("generic-user", "ADMIN");
        for (Runnable mutation : mutations(ACTOR)) {
            assertThatThrownBy(mutation::run).isInstanceOf(AccessDeniedException.class);
        }

        verifyNoInteractions(scopeQuery);
        verifyNoBusinessEffects();
    }

    @Test
    void absentScopeAndQueryFailureCannotReachBusinessData() {
        authenticate(ACTOR, "ADMIN");
        when(scopeQuery.findAuthorizationContext(ACTOR_ID))
                .thenReturn(Optional.empty())
                .thenThrow(new IllegalStateException("private database failure"));

        assertThatThrownBy(mutations(ACTOR).getFirst()::run)
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(mutations(ACTOR).get(2)::run)
                .isInstanceOf(OrganizationAdministrationUnavailableException.class)
                .hasMessageNotContaining("database");
        verifyNoBusinessEffects();
    }

    private void verifyNoBusinessEffects() {
        verifyNoInteractions(organizationQuery, organizationStore,
                branchQuery, branchStore, eventPublisher);
    }

    private List<Runnable> mutations(AuthenticatedActor actor) {
        return mutations(actor, 99);
    }

    private List<Runnable> mutations(AuthenticatedActor actor, long version) {
        return List.of(
                () -> organizationService.update(new UpdateOrganizationCommand(
                        "Changed Legal", "Coach Gym", null, null,
                        "America/El_Salvador", "USD", version), actor),
                () -> organizationService.changeStatus(new ChangeOrganizationStatusCommand(
                        OrganizationStatus.ACTIVE, "Review", version), actor),
                () -> branchService.create(new CreateGymBranchCommand(
                        "NORTH", "North Gym", null, null, null, null,
                        null, "SV", null, null, null), actor),
                () -> branchService.update(BRANCH_ID, new UpdateGymBranchCommand(
                        "Changed Branch", null, null, null, null, null, "SV",
                        null, null, "America/El_Salvador", version), actor),
                () -> branchService.changeStatus(BRANCH_ID, new ChangeGymBranchStatusCommand(
                        GymBranchStatus.INACTIVE, "Renovation", version), actor));
    }

    private static StaffAuthorizationContext context(
            RoleCode role, StaffAccountStatus status, StaffScopeType scope) {
        return new StaffAuthorizationContext(ACTOR_ID, Set.of(role), status, scope, Set.of());
    }

    private static void authenticate(Object principal, String role) {
        Object resolvedPrincipal = principal instanceof AuthenticatedActor actor
                ? (AuthenticatedActorProvider) () -> actor : principal;
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                resolvedPrincipal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    @EnableTransactionManagement
    @Import({OrganizationApplicationService.class, GymBranchApplicationService.class,
            OrganizationAdministrationAuthorization.class})
    static class TestConfiguration {

        @Bean StaffScopeQuery scopeQuery() { return mock(StaffScopeQuery.class); }
        @Bean OrganizationQuery organizationQuery() { return mock(OrganizationQuery.class); }
        @Bean OrganizationStore organizationStore() { return mock(OrganizationStore.class); }
        @Bean GymBranchQuery branchQuery() { return mock(GymBranchQuery.class); }
        @Bean GymBranchStore branchStore() { return mock(GymBranchStore.class); }
        @Bean ApplicationEventPublisher eventPublisher() { return mock(ApplicationEventPublisher.class); }
        @Bean Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new AbstractPlatformTransactionManager() {
                @Override protected Object doGetTransaction() { return new Object(); }
                @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
                @Override protected void doCommit(DefaultTransactionStatus status) { }
                @Override protected void doRollback(DefaultTransactionStatus status) { }
            };
        }
    }
}
