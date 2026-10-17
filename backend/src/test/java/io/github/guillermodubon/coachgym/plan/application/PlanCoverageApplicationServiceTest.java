package io.github.guillermodubon.coachgym.plan.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.plan.MembershipPlanBranchCoverageDetails;
import io.github.guillermodubon.coachgym.plan.MembershipPlanBranchCoverageScope;
import io.github.guillermodubon.coachgym.plan.MembershipPlanCoverageChanged;
import io.github.guillermodubon.coachgym.plan.UpdateMembershipPlanBranchCoverageCommand;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.BranchOperationContext;
import io.github.guillermodubon.coachgym.user.BranchOperationContextResolver;
import io.github.guillermodubon.coachgym.user.BranchResourceAuthorizationException;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffAuthorizationContext;
import io.github.guillermodubon.coachgym.user.StaffBranchAuthorizationException;
import io.github.guillermodubon.coachgym.user.StaffScopeQuery;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PlanCoverageApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID PLAN_ID = UUID.fromString("123e4567-e89b-42d3-a456-426614174001");
    private static final UUID ORG_ADMIN_ID = UUID.fromString("123e4567-e89b-42d3-a456-426614174002");
    private static final UUID BRANCH_ADMIN_ID = UUID.fromString("123e4567-e89b-42d3-a456-426614174003");
    private static final UUID BRANCH_ONE = UUID.fromString("123e4567-e89b-42d3-a456-426614174004");
    private static final UUID BRANCH_TWO = UUID.fromString("123e4567-e89b-42d3-a456-426614174005");
    private static final UUID ORGANIZATION_ID = UUID.fromString("123e4567-e89b-42d3-a456-426614174006");
    private static final AuthenticatedActor ORG_ADMIN = new AuthenticatedActor(ORG_ADMIN_ID, "org-admin");
    private static final AuthenticatedActor BRANCH_ADMIN = new AuthenticatedActor(BRANCH_ADMIN_ID, "branch-admin");

    @Mock
    private PlanStore planStore;

    @Mock
    private StaffScopeQuery staffScopeQuery;

    @Mock
    private BranchOperationContextResolver branchContextResolver;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PlanCoverageApplicationService service;

    @BeforeEach
    void setUp() {
        service = new PlanCoverageApplicationService(
                planStore, staffScopeQuery, branchContextResolver, eventPublisher, CLOCK);
    }

    @Test
    void organizationAdministratorReplacesCoverageAndPublishesOnlyMinimalEventOnce() {
        UpdateMembershipPlanBranchCoverageCommand command =
                new UpdateMembershipPlanBranchCoverageCommand(
                        MembershipPlanBranchCoverageScope.SELECTED_BRANCHES,
                        Set.of(BRANCH_ONE, BRANCH_TWO),
                        4);
        MembershipPlanBranchCoverageDetails updated = new MembershipPlanBranchCoverageDetails(
                PLAN_ID,
                MembershipPlanBranchCoverageScope.SELECTED_BRANCHES,
                Set.of(BRANCH_ONE, BRANCH_TWO),
                5);
        when(staffScopeQuery.findAuthorizationContext(ORG_ADMIN_ID))
                .thenReturn(Optional.of(organizationAdminContext()));
        when(branchContextResolver.resolveOperation(ORG_ADMIN_ID))
                .thenReturn(organizationContext(Set.of(BRANCH_ONE, BRANCH_TWO)));
        when(planStore.replaceBranchCoverage(PLAN_ID, command, ORG_ADMIN, NOW)).thenReturn(updated);

        assertThat(service.replaceCoverage(PLAN_ID, command, ORG_ADMIN)).isEqualTo(updated);

        verify(planStore).replaceBranchCoverage(PLAN_ID, command, ORG_ADMIN, NOW);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(MembershipPlanCoverageChanged.class);
        MembershipPlanCoverageChanged event =
                (MembershipPlanCoverageChanged) eventCaptor.getValue();
        assertThat(event.planId()).isEqualTo(PLAN_ID);
        assertThat(event.scope()).isEqualTo(MembershipPlanBranchCoverageScope.SELECTED_BRANCHES);
        assertThat(event.explicitBranchCount()).isEqualTo(2);
        assertThat(event.planVersion()).isEqualTo(5);
        assertThat(event.actorUserId()).isEqualTo(ORG_ADMIN_ID);
        assertThat(event.occurredAt()).isEqualTo(NOW);
    }

    @Test
    void branchAdministratorCannotChangeOrganizationPlanCoverage() {
        when(staffScopeQuery.findAuthorizationContext(BRANCH_ADMIN_ID))
                .thenReturn(Optional.of(branchAdminContext()));
        UpdateMembershipPlanBranchCoverageCommand command =
                new UpdateMembershipPlanBranchCoverageCommand(
                        MembershipPlanBranchCoverageScope.SINGLE_BRANCH,
                        Set.of(BRANCH_ONE),
                        0);

        assertThatThrownBy(() -> service.replaceCoverage(PLAN_ID, command, BRANCH_ADMIN))
                .isInstanceOf(StaffBranchAuthorizationException.class);

        verify(planStore, never()).replaceBranchCoverage(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void coverageMutationRejectsBranchesOutsideTheAdministratorsActiveCanonicalSet() {
        when(staffScopeQuery.findAuthorizationContext(ORG_ADMIN_ID))
                .thenReturn(Optional.of(organizationAdminContext()));
        when(branchContextResolver.resolveOperation(ORG_ADMIN_ID))
                .thenReturn(organizationContext(Set.of(BRANCH_ONE)));
        UpdateMembershipPlanBranchCoverageCommand command =
                new UpdateMembershipPlanBranchCoverageCommand(
                        MembershipPlanBranchCoverageScope.SINGLE_BRANCH,
                        Set.of(BRANCH_TWO),
                        0);

        assertThatThrownBy(() -> service.replaceCoverage(PLAN_ID, command, ORG_ADMIN))
                .isInstanceOf(io.github.guillermodubon.coachgym.plan.MembershipPlanBranchCoverageValidationException.class);

        verify(planStore, never()).replaceBranchCoverage(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void staleCoverageVersionDoesNotPublishAnEvent() {
        when(staffScopeQuery.findAuthorizationContext(ORG_ADMIN_ID))
                .thenReturn(Optional.of(organizationAdminContext()));
        when(branchContextResolver.resolveOperation(ORG_ADMIN_ID))
                .thenReturn(organizationContext(Set.of(BRANCH_ONE)));
        UpdateMembershipPlanBranchCoverageCommand command =
                new UpdateMembershipPlanBranchCoverageCommand(
                        MembershipPlanBranchCoverageScope.SINGLE_BRANCH,
                        Set.of(BRANCH_ONE),
                        1);
        when(planStore.replaceBranchCoverage(PLAN_ID, command, ORG_ADMIN, NOW))
                .thenThrow(new PlanVersionConflictException());

        assertThatThrownBy(() -> service.replaceCoverage(PLAN_ID, command, ORG_ADMIN))
                .isInstanceOf(PlanVersionConflictException.class);

        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void branchScopedPlanSearchIsPinnedToTheActiveBranch() {
        BranchOperationContext context = new BranchOperationContext(
                BRANCH_ADMIN_ID,
                ORGANIZATION_ID,
                StaffScopeType.BRANCH,
                BRANCH_ONE,
                Set.of(BRANCH_ONE));
        PlanSearchQuery query = PlanSearchQuery.from(true, "strength", 0, 20, "name", "asc");
        PlanPage page = new PlanPage(List.of(), 0, 20, 0, 0);
        when(branchContextResolver.resolveOperation(BRANCH_ADMIN_ID)).thenReturn(context);
        when(planStore.findAllForBranch(query, BRANCH_ONE)).thenReturn(page);

        assertThat(service.findVisiblePlans(query, null, BRANCH_ADMIN)).isEqualTo(page);

        verify(planStore).findAllForBranch(query, BRANCH_ONE);
    }

    @Test
    void branchScopedPlanSearchRejectsRequestedBranchSwitch() {
        BranchOperationContext context = new BranchOperationContext(
                BRANCH_ADMIN_ID,
                ORGANIZATION_ID,
                StaffScopeType.BRANCH,
                BRANCH_ONE,
                Set.of(BRANCH_ONE));
        when(branchContextResolver.resolveOperation(BRANCH_ADMIN_ID)).thenReturn(context);
        PlanSearchQuery query = PlanSearchQuery.from(null, null, 0, 20, "name", "asc");

        assertThatThrownBy(() -> service.findVisiblePlans(query, BRANCH_TWO, BRANCH_ADMIN))
                .isInstanceOf(BranchResourceAuthorizationException.class);

        verify(planStore, never()).findAllForBranch(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void organizationAdministratorIsPinnedToActiveBranchOrMayFilterAuthorizedBranch() {
        BranchOperationContext context = new BranchOperationContext(
                ORG_ADMIN_ID,
                ORGANIZATION_ID,
                StaffScopeType.ORGANIZATION,
                BRANCH_ONE,
                Set.of(BRANCH_ONE, BRANCH_TWO));
        PlanSearchQuery query = PlanSearchQuery.from(null, null, 0, 20, "name", "asc");
        PlanPage activeBranchPlans = new PlanPage(List.of(), 0, 20, 2, 1);
        PlanPage selectedBranchPlans = new PlanPage(List.of(), 0, 20, 3, 1);
        when(branchContextResolver.resolveOperation(ORG_ADMIN_ID)).thenReturn(context);
        when(planStore.findAllForBranch(query, BRANCH_ONE)).thenReturn(activeBranchPlans);
        when(planStore.findAllForBranch(query, BRANCH_TWO)).thenReturn(selectedBranchPlans);

        assertThat(service.findVisiblePlans(query, null, ORG_ADMIN)).isEqualTo(activeBranchPlans);
        assertThat(service.findVisiblePlans(query, BRANCH_TWO, ORG_ADMIN))
                .isEqualTo(selectedBranchPlans);
        verify(planStore, never()).findAll(query);
    }

    private static StaffAuthorizationContext organizationAdminContext() {
        return new StaffAuthorizationContext(
                ORG_ADMIN_ID,
                Set.of(RoleCode.ADMIN),
                StaffAccountStatus.ACTIVE,
                StaffScopeType.ORGANIZATION,
                Set.of());
    }

    private static StaffAuthorizationContext branchAdminContext() {
        return new StaffAuthorizationContext(
                BRANCH_ADMIN_ID,
                Set.of(RoleCode.ADMIN),
                StaffAccountStatus.ACTIVE,
                StaffScopeType.BRANCH,
                Set.of(BRANCH_ONE));
    }

    private static BranchOperationContext organizationContext(Set<UUID> branchIds) {
        return new BranchOperationContext(
                ORG_ADMIN_ID,
                ORGANIZATION_ID,
                StaffScopeType.ORGANIZATION,
                null,
                branchIds);
    }
}
