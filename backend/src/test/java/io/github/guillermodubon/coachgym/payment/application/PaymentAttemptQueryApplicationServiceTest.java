package io.github.guillermodubon.coachgym.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.user.ActiveBranchContextUnavailableException;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.BranchOperationContext;
import io.github.guillermodubon.coachgym.user.BranchOperationContextResolver;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentAttemptQueryApplicationServiceTest {

    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();

    private BranchOperationContextResolver contextResolver;
    private PaymentAttemptReadQuery readQuery;
    private PaymentAttemptQueryApplicationService service;

    @BeforeEach
    void setUp() {
        contextResolver = mock(BranchOperationContextResolver.class);
        readQuery = mock(PaymentAttemptReadQuery.class);
        service = new PaymentAttemptQueryApplicationService(contextResolver, readQuery);
    }

    @Test
    void resolvesBranchFromAuthenticatedActorAndPassesItAsTheMandatoryScope() {
        when(contextResolver.resolveOperation(ACTOR_ID)).thenReturn(new BranchOperationContext(
                ACTOR_ID, ORGANIZATION_ID, StaffScopeType.ORGANIZATION,
                BRANCH_ID, Set.of(BRANCH_ID)));
        PaymentAttemptSearchQuery query = query();
        PaymentAttemptPage expected = new PaymentAttemptPage(List.of(), 0, 25, 0, 0);
        when(readQuery.findPage(query, BRANCH_ID)).thenReturn(expected);

        assertThat(service.findPage(query, actor())).isSameAs(expected);
        verify(contextResolver).resolveOperation(ACTOR_ID);
        verify(readQuery).findPage(query, BRANCH_ID);
    }

    @Test
    void rejectsMissingActiveBranchWithoutCallingPersistence() {
        when(contextResolver.resolveOperation(ACTOR_ID)).thenReturn(new BranchOperationContext(
                ACTOR_ID, ORGANIZATION_ID, StaffScopeType.ORGANIZATION,
                null, Set.of(BRANCH_ID)));
        PaymentAttemptSearchQuery query = query();

        assertThatThrownBy(() -> service.findPage(query, actor()))
                .isInstanceOf(ActiveBranchContextUnavailableException.class);
        verify(readQuery, never()).findPage(query, BRANCH_ID);
    }

    private static PaymentAttemptSearchQuery query() {
        return new PaymentAttemptSearchQuery(
                UUID.randomUUID(), UUID.randomUUID(), null, null, 0, 25);
    }

    private static AuthenticatedActor actor() {
        return new AuthenticatedActor(ACTOR_ID, "payment-query-test");
    }
}
