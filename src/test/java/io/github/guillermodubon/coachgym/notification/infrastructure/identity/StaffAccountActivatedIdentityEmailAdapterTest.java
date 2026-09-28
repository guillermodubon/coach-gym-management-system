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
import io.github.guillermodubon.coachgym.shared.identityemail.IdentityEmailDeliveryStatus;
import io.github.guillermodubon.coachgym.shared.identityemail.StaffAccountActivatedEmail;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StaffAccountActivatedIdentityEmailAdapterTest {

    @Test
    void composesTokenFreeEscapedActivationNoticeWithProviderNeutralSender() {
        EmailSender sender = mock(EmailSender.class);
        when(sender.send(any(EmailMessage.class))).thenReturn(EmailSendResult.sent());
        var activation = new StaffAccountActivatedEmail(
                "staff@example.test", "Ada <Coach>", "RECEPTIONIST", "BRANCH",
                Instant.parse("2026-09-25T12:00:00Z"));
        var adapter = new StaffAccountActivatedIdentityEmailAdapter(sender, emailProperties());

        assertThat(adapter.sendAccountActivated(activation))
                .isEqualTo(IdentityEmailDeliveryStatus.SENT);

        ArgumentCaptor<EmailMessage> captured = ArgumentCaptor.forClass(EmailMessage.class);
        verify(sender).send(captured.capture());
        EmailMessage message = captured.getValue();
        assertThat(message.recipient()).isEqualTo("staff@example.test");
        assertThat(message.htmlBody()).contains("Ada &lt;Coach&gt;")
                .doesNotContain("Ada <Coach>");
        assertThat(message.plainTextBody()).contains("RECEPTIONIST", "BRANCH");
        assertThat(message.attachment()).isNull();
        assertThat(message.toString()).doesNotContain("staff@example.test", "activated");
        assertThat(activation.toString()).doesNotContain("staff@example.test", "Ada");
    }

    private static EmailProperties emailProperties() {
        return new EmailProperties(true, "smtp", "Coach Gym", "v1", "no-reply@example.test", "Coach Gym",
                null, "localhost", 1025, null, null, false, false,
                Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(10),
                12 * 1024 * 1024, 10 * 1024 * 1024, 200, 254, 3, Duration.ofMinutes(15));
    }
}
