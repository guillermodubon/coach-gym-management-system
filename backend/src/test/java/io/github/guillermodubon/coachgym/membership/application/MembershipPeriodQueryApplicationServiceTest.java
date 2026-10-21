package io.github.guillermodubon.coachgym.membership.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.membership.MembershipDetails;
import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MembershipPeriodQueryApplicationServiceTest {

    private static final UUID MEMBERSHIP_ID = UUID.randomUUID();
    private static final UUID PERIOD_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    private MembershipApplicationService membershipService;
    private MembershipPeriodReadQuery readQuery;
    private MembershipPeriodQueryApplicationService service;

    @BeforeEach
    void setUp() {
        membershipService = mock(MembershipApplicationService.class);
        readQuery = mock(MembershipPeriodReadQuery.class);
        service = new MembershipPeriodQueryApplicationService(membershipService, readQuery);
        when(membershipService.findById(MEMBERSHIP_ID, actor())).thenReturn(parentMembership());
    }

    @Test
    void authorizesParentBeforePassingItsPersistedBranchToPageQuery() {
        MembershipPeriodSearchQuery query = new MembershipPeriodSearchQuery(
                0, 25, MembershipSortDirection.DESC);
        MembershipPeriodPage expected = new MembershipPeriodPage(java.util.List.of(), 0, 25, 0, 0);
        when(readQuery.findPage(MEMBERSHIP_ID, BRANCH_ID, query)).thenReturn(expected);

        assertThat(service.findPage(MEMBERSHIP_ID, query, actor())).isSameAs(expected);

        verify(membershipService).findById(MEMBERSHIP_ID, actor());
        verify(readQuery).findPage(MEMBERSHIP_ID, BRANCH_ID, query);
    }

    @Test
    void scopesDetailToMembershipAndAuthorizedBranchAndReturnsSafeNotFound() {
        when(readQuery.findById(MEMBERSHIP_ID, PERIOD_ID, BRANCH_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(MEMBERSHIP_ID, PERIOD_ID, actor()))
                .isInstanceOf(MembershipPeriodNotFoundException.class);

        verify(readQuery).findById(MEMBERSHIP_ID, PERIOD_ID, BRANCH_ID);
    }

    private static MembershipDetails parentMembership() {
        Instant now = Instant.parse("2026-10-19T00:00:00Z");
        return new MembershipDetails(
                MEMBERSHIP_ID,
                "MEM-000001",
                UUID.randomUUID(),
                MembershipStatus.CANCELLED,
                null,
                now,
                now,
                2,
                BRANCH_ID);
    }

    private static AuthenticatedActor actor() {
        return new AuthenticatedActor(ACTOR_ID, "period-history-test");
    }
}
