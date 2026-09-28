package io.github.guillermodubon.coachgym.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import io.github.guillermodubon.coachgym.user.AcceptStaffInvitationCommand;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffInvitationAccepted;
import io.github.guillermodubon.coachgym.user.application.StaffIdentityAbuseService;
import io.github.guillermodubon.coachgym.user.StaffInvitationAcceptanceResult;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class StaffInvitationAcceptanceApplicationServiceTest {

    private static final String TOKEN = "A".repeat(43);
    private static final UUID USER_ID = UUID.fromString("84000000-0000-0000-0000-000000000001");
    private static final UUID INVITATION_ID = UUID.fromString("84000000-0000-0000-0000-000000000002");
    private static final UUID ORGANIZATION_ID = UUID.fromString("84000000-0000-0000-0000-000000000003");
    private static final UUID INVITER_ID = UUID.fromString("84000000-0000-0000-0000-000000000004");
    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

    @Test
    void publishesSafeEventAndQueuesActivationNoticeOnlyAfterTransactionReturns() {
        StaffInvitationAcceptanceTransactionService transactions =
                mock(StaffInvitationAcceptanceTransactionService.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        StaffAccountActivationDeliveryService delivery = mock(StaffAccountActivationDeliveryService.class);
        StaffIdentityAbuseService abuse = mock(StaffIdentityAbuseService.class);
        when(abuse.invitationAttemptAllowed(TOKEN, "127.0.0.1")).thenReturn(true);
        StaffInvitationAcceptanceApplicationService service =
                new StaffInvitationAcceptanceApplicationService(transactions, events, delivery, abuse);
        StaffInvitationAccepted event = event();
        StaffInvitationAcceptanceResult result = result();
        when(transactions.accept(org.mockito.ArgumentMatchers.any(AcceptStaffInvitationCommand.class)))
                .thenReturn(new PreparedStaffInvitationAcceptance(result, event));

        StaffInvitationAcceptanceResult accepted = service.accept(command(), "127.0.0.1");

        assertThat(accepted).isEqualTo(result);
        assertThat(accepted.toString()).doesNotContain(TOKEN, "ada@example.test");
        assertThat(event.toString()).doesNotContain(TOKEN, "ada@example.test");
        var order = inOrder(transactions, events, delivery);
        order.verify(transactions).accept(org.mockito.ArgumentMatchers.any());
        order.verify(events).publishEvent(event);
        order.verify(delivery).sendAfterCommit(INVITATION_ID);
    }

    @Test
    void eventPublicationFailureDoesNotChangeCommittedAcceptanceResult() {
        StaffInvitationAcceptanceTransactionService transactions =
                mock(StaffInvitationAcceptanceTransactionService.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        StaffAccountActivationDeliveryService delivery = mock(StaffAccountActivationDeliveryService.class);
        StaffIdentityAbuseService abuse = mock(StaffIdentityAbuseService.class);
        when(abuse.invitationAttemptAllowed(TOKEN, "127.0.0.1")).thenReturn(true);
        StaffInvitationAcceptanceApplicationService service =
                new StaffInvitationAcceptanceApplicationService(transactions, events, delivery, abuse);
        when(transactions.accept(org.mockito.ArgumentMatchers.any(AcceptStaffInvitationCommand.class)))
                .thenReturn(new PreparedStaffInvitationAcceptance(result(), event()));
        doThrow(new IllegalStateException("event detail must not escape"))
                .when(events).publishEvent(org.mockito.ArgumentMatchers.any(Object.class));

        assertThat(service.accept(command(), "127.0.0.1")).isEqualTo(result());
        org.mockito.Mockito.verify(delivery).sendAfterCommit(INVITATION_ID);
    }

    @Test
    void internalRetryHookDelegatesOnlyTheCanonicalInvitationIdentifier() {
        StaffAccountActivationDeliveryService delivery =
                mock(StaffAccountActivationDeliveryService.class);
        StaffInvitationAcceptanceTransactionService transactions =
                mock(StaffInvitationAcceptanceTransactionService.class);
        when(delivery.attemptIfDue(INVITATION_ID)).thenReturn(true);
        StaffInvitationAcceptanceApplicationService service =
                new StaffInvitationAcceptanceApplicationService(
                        transactions,
                        mock(ApplicationEventPublisher.class),
                        delivery,
                        mock(StaffIdentityAbuseService.class));

        assertThat(service.retryActivationNotice(INVITATION_ID)).isTrue();

        org.mockito.Mockito.verify(delivery).attemptIfDue(INVITATION_ID);
        org.mockito.Mockito.verify(transactions, never()).accept(
                org.mockito.ArgumentMatchers.any(AcceptStaffInvitationCommand.class));
    }

    @Test
    void unavailableInvitationFailureUsesTheSameSafeConflictAndRecordsFailureSubjects() {
        StaffIdentityAbuseService abuse = mock(StaffIdentityAbuseService.class);
        when(abuse.invitationAttemptAllowed(TOKEN, "127.0.0.1")).thenReturn(true);
        StaffInvitationAcceptanceTransactionService transactions =
                mock(StaffInvitationAcceptanceTransactionService.class);
        when(transactions.inspect(TOKEN)).thenThrow(
                new io.github.guillermodubon.coachgym.user.StaffIdentityStateConflictException(
                        "Invitation is not available."));
        StaffInvitationAcceptanceApplicationService service =
                new StaffInvitationAcceptanceApplicationService(
                        transactions,
                        mock(ApplicationEventPublisher.class),
                        mock(StaffAccountActivationDeliveryService.class),
                        abuse);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> service.inspect(TOKEN, "127.0.0.1")))
                .isInstanceOf(io.github.guillermodubon.coachgym.user.StaffIdentityStateConflictException.class)
                .hasMessage("Invitation is not available.");
        verify(abuse).recordInvitationFailure(TOKEN, "127.0.0.1");
    }

    private static AcceptStaffInvitationCommand command() {
        return new AcceptStaffInvitationCommand(
                TOKEN, "a-long-test-password-123", "a-long-test-password-123", "Ada", "Lovelace");
    }

    private static StaffInvitationAccepted event() {
        return new StaffInvitationAccepted(
                INVITATION_ID, ORGANIZATION_ID, USER_ID, INVITER_ID,
                RoleCode.ADMIN, StaffScopeType.ORGANIZATION, Set.of(), NOW);
    }

    private static StaffInvitationAcceptanceResult result() {
        return new StaffInvitationAcceptanceResult(
                USER_ID, RoleCode.ADMIN, StaffScopeType.ORGANIZATION, Set.of(), NOW);
    }
}
