package io.github.guillermodubon.coachgym.notification.infrastructure.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.notification.EmailMessage;
import io.github.guillermodubon.coachgym.notification.EmailSendResult;
import io.github.guillermodubon.coachgym.notification.application.EmailSender;
import io.github.guillermodubon.coachgym.notification.infrastructure.email.EmailProperties;
import io.github.guillermodubon.coachgym.notification.infrastructure.email.IdentityEmailProperties;
import io.github.guillermodubon.coachgym.shared.identityemail.IdentityEmailDeliveryStatus;
import io.github.guillermodubon.coachgym.shared.identityemail.StaffInvitationEmail;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StaffInvitationIdentityEmailAdapterTest {

    private static final String TOKEN = "A".repeat(43);

    @Test
    void composesEphemeralTokenLinkAndEscapesOrganizationData() {
        EmailSender sender = mock(EmailSender.class);
        when(sender.send(any(EmailMessage.class))).thenReturn(EmailSendResult.sent());
        StaffInvitationIdentityEmailAdapter adapter = new StaffInvitationIdentityEmailAdapter(
                sender,
                emailProperties(),
                new IdentityEmailProperties("https://staff.example.test/accept-invitation"));
        StaffInvitationEmail invitation = new StaffInvitationEmail(
                "private@example.test", "RECEPTIONIST", "BRANCH", java.util.List.of("North <Office>"),
                Instant.parse("2026-09-25T12:00:00Z"), TOKEN);

        assertThat(adapter.sendInvitation(invitation)).isEqualTo(IdentityEmailDeliveryStatus.SENT);

        ArgumentCaptor<EmailMessage> captured = ArgumentCaptor.forClass(EmailMessage.class);
        verify(sender).send(captured.capture());
        EmailMessage message = captured.getValue();
        assertThat(message.plainTextBody()).contains("https://staff.example.test/accept-invitation#token=" + TOKEN)
                .contains("North <Office>");
        assertThat(message.htmlBody()).contains("North &lt;Office&gt;", "#token=" + TOKEN);
        assertThat(message.htmlBody()).doesNotContain("North <Office>");
        assertThat(message.attachment()).isNull();
        assertThat(message.toString()).doesNotContain(TOKEN, "private@example.test");
        assertThat(invitation.toString()).doesNotContain(TOKEN, "private@example.test");
    }

    @Test
    void rejectsUntrustedAcceptanceOriginsAndTokenQueries() {
        assertThat(new IdentityEmailProperties("http://external.example.test/accept-invitation")
                .isInvitationAcceptanceUrlValid()).isFalse();
        assertThat(new IdentityEmailProperties(
                "https://staff.example.test/accept-invitation?token=not-allowed")
                .isInvitationAcceptanceUrlValid()).isFalse();
        assertThat(new IdentityEmailProperties("http://localhost:5173/accept-invitation")
                .isInvitationAcceptanceUrlValid()).isTrue();
    }

    private static EmailProperties emailProperties() {
        return new EmailProperties(true, "Coach Gym", "v1", "no-reply@example.test", "Coach Gym",
                null, 10 * 1024 * 1024, 200, 3, Duration.ofMinutes(15));
    }
}
