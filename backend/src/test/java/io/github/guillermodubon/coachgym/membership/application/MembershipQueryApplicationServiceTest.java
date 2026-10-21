package io.github.guillermodubon.coachgym.membership.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.user.ActiveBranchContextUnavailableException;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.BranchOperationContext;
import io.github.guillermodubon.coachgym.user.BranchOperationContextResolver;
import io.github.guillermodubon.coachgym.user.BranchResourceAuthorizationException;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MembershipQueryApplicationServiceTest {

    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID ACTIVE_BRANCH_ID = UUID.randomUUID();
    private static final UUID OTHER_BRANCH_ID = UUID.randomUUID();

    private BranchOperationContextResolver contextResolver;
    private MembershipReadQuery readQuery;
    private MembershipQueryApplicationService service;

    @BeforeEach
    void setUp() {
        contextResolver = mock(BranchOperationContextResolver.class);
        readQuery = mock(MembershipReadQuery.class);
        service = new MembershipQueryApplicationService(contextResolver, readQuery);
    }

    @Test
    void requiresActiveBranchWhenFilterIsOmitted() {
        when(contextResolver.resolveOperation(ACTOR_ID)).thenReturn(context(
                StaffScopeType.ORGANIZATION, null, Set.of(ACTIVE_BRANCH_ID)));
        MembershipSearchQuery query = query(null);

        assertThatThrownBy(() -> service.findPage(query, actor()))
                .isInstanceOf(ActiveBranchContextUnavailableException.class);
        verify(readQuery, never()).findPage(query, ACTIVE_BRANCH_ID);
    }

    @Test
    void allowsOrganizationAdminToQueryAnExplicitAuthorizedBranch() {
        when(contextResolver.resolveOperation(ACTOR_ID)).thenReturn(context(
                StaffScopeType.ORGANIZATION,
                ACTIVE_BRANCH_ID,
                Set.of(ACTIVE_BRANCH_ID, OTHER_BRANCH_ID)));
        MembershipSearchQuery query = query(OTHER_BRANCH_ID);
        MembershipPage expected = new MembershipPage(java.util.List.of(), 0, 25, 0, 0);
        when(readQuery.findPage(query, OTHER_BRANCH_ID)).thenReturn(expected);

        assertThat(service.findPage(query, actor())).isSameAs(expected);
        verify(readQuery).findPage(query, OTHER_BRANCH_ID);
    }

    @Test
    void preventsBranchScopedStaffFromSwitchingBranchesThroughAFilter() {
        when(contextResolver.resolveOperation(ACTOR_ID)).thenReturn(context(
                StaffScopeType.BRANCH,
                ACTIVE_BRANCH_ID,
                Set.of(ACTIVE_BRANCH_ID)));
        MembershipSearchQuery query = query(OTHER_BRANCH_ID);

        assertThatThrownBy(() -> service.findPage(query, actor()))
                .isInstanceOf(BranchResourceAuthorizationException.class);
        verify(readQuery, never()).findPage(query, OTHER_BRANCH_ID);
    }

    @Test
    void usesActiveBranchAsTheMandatoryEffectiveFilter() {
        when(contextResolver.resolveOperation(ACTOR_ID)).thenReturn(context(
                StaffScopeType.BRANCH,
                ACTIVE_BRANCH_ID,
                Set.of(ACTIVE_BRANCH_ID)));
        MembershipSearchQuery query = query(null);
        MembershipPage expected = new MembershipPage(java.util.List.of(), 0, 25, 0, 0);
        when(readQuery.findPage(query, ACTIVE_BRANCH_ID)).thenReturn(expected);

        assertThat(service.findPage(query, actor())).isSameAs(expected);
        verify(readQuery).findPage(query, ACTIVE_BRANCH_ID);
    }

    private static BranchOperationContext context(
            StaffScopeType scopeType,
            UUID activeBranchId,
            Set<UUID> authorizedBranches) {
        return new BranchOperationContext(
                ACTOR_ID, ORGANIZATION_ID, scopeType, activeBranchId, authorizedBranches);
    }

    private static MembershipSearchQuery query(UUID branchId) {
        return new MembershipSearchQuery(
                null, null, MembershipStatus.ACTIVE, branchId, 0, 25,
                MembershipSortField.CREATED_AT, MembershipSortDirection.DESC);
    }

    private static AuthenticatedActor actor() {
        return new AuthenticatedActor(ACTOR_ID, "membership-test");
    }
}
