package io.github.guillermodubon.coachgym.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.shared.identityemail.IdentityEmailDeliveryStatus;
import io.github.guillermodubon.coachgym.shared.identityemail.StaffInvitationEmail;
import io.github.guillermodubon.coachgym.shared.identityemail.StaffInvitationEmailSender;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.CreateStaffInvitationCommand;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffInvitationCreated;
import io.github.guillermodubon.coachgym.user.StaffInvitationDeliveryAttempted;
import io.github.guillermodubon.coachgym.user.StaffInvitationDeliveryStatus;
import io.github.guillermodubon.coachgym.user.StaffInvitationDetails;
import io.github.guillermodubon.coachgym.user.StaffInvitationPolicy;
import io.github.guillermodubon.coachgym.user.StaffInvitationStatus;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

class StaffInvitationApplicationServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("82000000-0000-0000-0000-000000000001");
    private static final UUID ORGANIZATION_ID = UUID.fromString("82000000-0000-0000-0000-000000000002");
    private static final UUID INVITATION_ID = UUID.fromString("82000000-0000-0000-0000-000000000003");
    private static final UUID BRANCH_ID = UUID.fromString("82000000-0000-0000-0000-000000000004");
    private static final String TOKEN = "A".repeat(43);
    private static final String EMAIL = "private@example.test";
    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

    private StaffInvitationTransactionService transactions;
    private StaffInvitationEmailSender emailSender;
    private ApplicationEventPublisher events;
    private StaffInvitationApplicationService service;
    private PreparedStaffInvitationDelivery prepared;

    @BeforeEach
    void setUp() {
        transactions = mock(StaffInvitationTransactionService.class);
        emailSender = mock(StaffInvitationEmailSender.class);
        events = mock(ApplicationEventPublisher.class);
        service = new StaffInvitationApplicationService(
                transactions, emailSender, events, Clock.fixed(NOW, ZoneOffset.UTC));
        StaffInvitationRecord invitation = new StaffInvitationRecord(
                INVITATION_ID, ORGANIZATION_ID, EMAIL, RoleCode.RECEPTIONIST,
                StaffScopeType.BRANCH, Set.of(BRANCH_ID), StaffInvitationStatus.PENDING,
                new StaffTokenFingerprint("b".repeat(64), StaffTokenPurpose.INVITATION.schemeVersion()),
                NOW, NOW, NOW.plus(StaffInvitationPolicy.RECEPTIONIST_INVITATION_LIFETIME),
                null, null, null, null, ACTOR_ID, 0);
        prepared = new PreparedStaffInvitationDelivery(
                invitation,
                new StaffInvitationEmail(
                        EMAIL, "RECEPTIONIST", "BRANCH", List.of("North"), invitation.expiresAt(), TOKEN),
                NOW);
    }

    @Test
    void commitsPreparedInvitationBeforeDeliveryAndPublishesOnlySafeEvents() {
        AuthenticatedActor actor = new AuthenticatedActor(ACTOR_ID, "organization-admin");
        CreateStaffInvitationCommand command = new CreateStaffInvitationCommand(
                EMAIL, RoleCode.RECEPTIONIST, StaffScopeType.BRANCH, Set.of(BRANCH_ID));
        when(transactions.create(command, actor, null)).thenReturn(prepared);
        when(emailSender.sendInvitation(prepared.email())).thenReturn(IdentityEmailDeliveryStatus.SENT);

        var result = service.create(command, actor, null);

        assertThat(result.deliveryStatus()).isEqualTo(StaffInvitationDeliveryStatus.SENT);
        assertThat(result.invitation().invitationId()).isEqualTo(INVITATION_ID);
        assertThat(result.toString()).doesNotContain(TOKEN);
        InOrder order = inOrder(transactions, emailSender);
        order.verify(transactions).create(command, actor, null);
        order.verify(emailSender).sendInvitation(prepared.email());
        verify(transactions).completeDelivery(
                INVITATION_ID, 0, StaffInvitationDeliveryStatus.SENT, NOW);

        ArgumentCaptor<Object> captured = ArgumentCaptor.forClass(Object.class);
        verify(events, times(2)).publishEvent(captured.capture());
        assertThat(captured.getAllValues()).allSatisfy(event ->
                assertThat(event.toString()).doesNotContain(TOKEN, EMAIL));
        assertThat(captured.getAllValues()).anyMatch(StaffInvitationCreated.class::isInstance)
                .anyMatch(StaffInvitationDeliveryAttempted.class::isInstance);
    }

    @Test
    void thrownTransportFailureIsAmbiguousAndDoesNotRetryTheLink() {
        AuthenticatedActor actor = new AuthenticatedActor(ACTOR_ID, "organization-admin");
        CreateStaffInvitationCommand command = new CreateStaffInvitationCommand(
                EMAIL, RoleCode.RECEPTIONIST, StaffScopeType.BRANCH, Set.of(BRANCH_ID));
        when(transactions.create(command, actor, null)).thenReturn(prepared);
        when(emailSender.sendInvitation(any(StaffInvitationEmail.class)))
                .thenThrow(new IllegalStateException("private transport detail"));

        var result = service.create(command, actor, null);

        assertThat(result.deliveryStatus()).isEqualTo(StaffInvitationDeliveryStatus.AMBIGUOUS);
        verify(emailSender, times(1)).sendInvitation(prepared.email());
        verify(transactions).completeDelivery(
                INVITATION_ID, 0, StaffInvitationDeliveryStatus.AMBIGUOUS, NOW);
    }
}
