package io.github.guillermodubon.coachgym.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.shared.identityemail.IdentityEmailDeliveryStatus;
import io.github.guillermodubon.coachgym.shared.identityemail.StaffAccountActivatedEmail;
import io.github.guillermodubon.coachgym.shared.identityemail.StaffAccountActivatedEmailSender;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffAccountActivationDeliveryServiceTest {

    private static final UUID INVITATION_ID = UUID.fromString("85000000-0000-0000-0000-000000000001");
    private static final UUID DELIVERY_ID = UUID.fromString("85000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

    @Test
    void confirmedFailureIsDurableAndGetsBoundedRetryTime() {
        StaffAccountActivationDeliveryStore store = mock(StaffAccountActivationDeliveryStore.class);
        StaffAccountActivatedEmailSender sender = mock(StaffAccountActivatedEmailSender.class);
        var email = email();
        when(store.claim(INVITATION_ID, NOW, NOW.plusSeconds(120)))
                .thenReturn(Optional.of(new StaffAccountActivationDeliveryClaim(DELIVERY_ID, 1, email)));
        when(sender.sendAccountActivated(email)).thenReturn(IdentityEmailDeliveryStatus.FAILED);
        var service = service(store, sender);

        assertThat(service.attemptIfDue(INVITATION_ID)).isFalse();

        org.mockito.ArgumentCaptor<Instant> retryAt =
                org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(store).complete(
                org.mockito.ArgumentMatchers.eq(DELIVERY_ID),
                org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(IdentityEmailDeliveryStatus.FAILED),
                org.mockito.ArgumentMatchers.eq(NOW),
                retryAt.capture());
        assertThat(retryAt.getValue()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void ambiguousTransportOutcomeIsNotAutomaticallyRetried() {
        StaffAccountActivationDeliveryStore store = mock(StaffAccountActivationDeliveryStore.class);
        StaffAccountActivatedEmailSender sender = mock(StaffAccountActivatedEmailSender.class);
        when(store.claim(INVITATION_ID, NOW, NOW.plusSeconds(120)))
                .thenReturn(Optional.of(new StaffAccountActivationDeliveryClaim(DELIVERY_ID, 1, email())));
        when(sender.sendAccountActivated(email()))
                .thenThrow(new IllegalStateException("private provider diagnostic"));
        var service = service(store, sender);

        assertThat(service.attemptIfDue(INVITATION_ID)).isFalse();

        verify(store).complete(DELIVERY_ID, 1, IdentityEmailDeliveryStatus.AMBIGUOUS, NOW, null);
    }

    @Test
    void unavailableClaimDoesNotSend() {
        StaffAccountActivationDeliveryStore store = mock(StaffAccountActivationDeliveryStore.class);
        StaffAccountActivatedEmailSender sender = mock(StaffAccountActivatedEmailSender.class);
        when(store.claim(INVITATION_ID, NOW, NOW.plusSeconds(120))).thenReturn(Optional.empty());

        assertThat(service(store, sender).attemptIfDue(INVITATION_ID)).isFalse();

        verify(sender, never()).sendAccountActivated(org.mockito.ArgumentMatchers.any());
        verify(store, never()).complete(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void retryDelayGrowsAndAttemptLimitIsEnforced() {
        StaffAccountActivationRetryPolicy policy = new StaffAccountActivationRetryPolicy();

        assertThat(policy.retryAt(1, NOW)).isEqualTo(NOW.plusSeconds(60));
        assertThat(policy.retryAt(2, NOW)).isEqualTo(NOW.plusSeconds(120));
        assertThat(policy.retryAt(4, NOW)).isEqualTo(NOW.plusSeconds(480));
        assertThatThrownBy(() -> policy.retryAt(5, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static StaffAccountActivationDeliveryService service(
            StaffAccountActivationDeliveryStore store,
            StaffAccountActivatedEmailSender sender) {
        return new StaffAccountActivationDeliveryService(
                store,
                sender,
                new StaffAccountActivationRetryPolicy(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static StaffAccountActivatedEmail email() {
        return new StaffAccountActivatedEmail(
                "activation@example.test", "Ada Lovelace", "RECEPTIONIST", "BRANCH", NOW);
    }
}
