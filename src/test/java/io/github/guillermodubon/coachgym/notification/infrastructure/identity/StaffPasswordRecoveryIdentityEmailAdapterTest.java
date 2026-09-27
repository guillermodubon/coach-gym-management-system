package io.github.guillermodubon.coachgym.notification.infrastructure.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.notification.EmailMessage;
import io.github.guillermodubon.coachgym.notification.EmailSendResult;
import io.github.guillermodubon.coachgym.notification.application.EmailSender;
import io.github.guillermodubon.coachgym.notification.infrastructure.smtp.EmailProperties;
import io.github.guillermodubon.coachgym.notification.infrastructure.smtp.IdentityEmailProperties;
import io.github.guillermodubon.coachgym.shared.identityemail.StaffPasswordRecoveryEmail;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StaffPasswordRecoveryIdentityEmailAdapterTest {

    private static final String TOKEN = "A".repeat(43);

    @Test
    void composesEphemeralFragmentLinkEscapesDisplayDataAndRedactsMessageDiagnostics() {
        EmailSender sender = mock(EmailSender.class);
        when(sender.send(any(EmailMessage.class))).thenReturn(EmailSendResult.sent());
        StaffPasswordRecoveryIdentityEmailAdapter adapter = new StaffPasswordRecoveryIdentityEmailAdapter(
                sender,
                emailProperties(),
                new IdentityEmailProperties(
                        "https://staff.example.test/accept-invitation",
                        "https://staff.example.test/recover-password"));
        StaffPasswordRecoveryEmail recovery = new StaffPasswordRecoveryEmail(
                "private@example.test", "Ada <Lovelace>", TOKEN,
                Instant.parse("2026-09-25T12:15:00Z"));

        adapter.sendPasswordRecoveryLink(recovery);

        ArgumentCaptor<EmailMessage> captured = ArgumentCaptor.forClass(EmailMessage.class);
        verify(sender).send(captured.capture());
        EmailMessage message = captured.getValue();
        assertThat(message.plainTextBody())
                .contains("https://staff.example.test/recover-password#token=" + TOKEN);
        assertThat(message.htmlBody()).contains("Ada &lt;Lovelace&gt;", "#token=" + TOKEN)
                .doesNotContain("Ada <Lovelace>");
        assertThat(message.toString()).doesNotContain(TOKEN, "private@example.test");
        assertThat(recovery.toString()).doesNotContain(TOKEN, "private@example.test");
    }

    @Test
    void transportFailureIsSwallowedWithoutLoggingTokenOrRecipient() {
        EmailSender sender = mock(EmailSender.class);
        when(sender.send(any(EmailMessage.class))).thenThrow(new IllegalStateException("transport detail"));
        StaffPasswordRecoveryIdentityEmailAdapter adapter = new StaffPasswordRecoveryIdentityEmailAdapter(
                sender,
                emailProperties(),
                new IdentityEmailProperties(
                        "https://staff.example.test/accept-invitation",
                        "https://staff.example.test/recover-password"));

        adapter.sendPasswordRecoveryLink(new StaffPasswordRecoveryEmail(
                "private@example.test", "Ada Lovelace", TOKEN,
                Instant.parse("2026-09-25T12:15:00Z")));

        verify(sender).send(any(EmailMessage.class));
    }

    private static EmailProperties emailProperties() {
        return new EmailProperties(true, "smtp", "Coach Gym", "v1", "no-reply@example.test", "Coach Gym",
                null, "localhost", 1025, null, null, false, false,
                Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(10),
                12 * 1024 * 1024, 10 * 1024 * 1024, 200, 254, 3, Duration.ofMinutes(15));
    }
}
