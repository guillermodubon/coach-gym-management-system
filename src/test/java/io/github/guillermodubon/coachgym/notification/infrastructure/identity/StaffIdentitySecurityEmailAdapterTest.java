package io.github.guillermodubon.coachgym.notification.infrastructure.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.notification.EmailAttemptResult;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryFailureCode;
import io.github.guillermodubon.coachgym.notification.EmailMessage;
import io.github.guillermodubon.coachgym.notification.EmailSendResult;
import io.github.guillermodubon.coachgym.notification.application.EmailSender;
import io.github.guillermodubon.coachgym.notification.infrastructure.email.EmailProperties;
import io.github.guillermodubon.coachgym.shared.identityemail.IdentityEmailDeliveryStatus;
import io.github.guillermodubon.coachgym.shared.identityemail.IdentitySecurityNoticeType;
import io.github.guillermodubon.coachgym.shared.identityemail.StaffIdentitySecurityEmail;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StaffIdentitySecurityEmailAdapterTest {

    @Mock private EmailSender emailSender;

    @Test
    void composesEscapedProviderNeutralAuthorityNoticeWithoutReasonOrCredentialData() {
        when(emailSender.send(any())).thenReturn(EmailSendResult.sent());
        StaffIdentitySecurityEmailAdapter adapter = new StaffIdentitySecurityEmailAdapter(
                emailSender, properties());
        StaffIdentitySecurityEmail notice = new StaffIdentitySecurityEmail(
                "staff@example.test", "<Staff & Person>", IdentitySecurityNoticeType.AUTHORITY_CHANGED,
                "ADMIN", "BRANCH", Instant.parse("2026-09-21T12:00:00Z"));

        assertThat(adapter.sendSecurityNotice(notice)).isEqualTo(IdentityEmailDeliveryStatus.SENT);

        ArgumentCaptor<EmailMessage> message = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender).send(message.capture());
        assertThat(message.getValue().htmlBody())
                .contains("&lt;Staff &amp; Person&gt;", "ADMIN / BRANCH")
                .doesNotContain("<Staff & Person>", "password", "reason");
        assertThat(notice.toString()).doesNotContain("staff@example.test", "<Staff", "password");
    }

    @Test
    void mapsSafeTransportFailureWithoutLeakingFailureMessage() {
        when(emailSender.send(any())).thenReturn(EmailSendResult.failed(
                EmailDeliveryFailureCode.TRANSPORT_CONFIGURATION_FAILED,
                "mail transport unavailable"));
        StaffIdentitySecurityEmailAdapter adapter = new StaffIdentitySecurityEmailAdapter(
                emailSender, properties());
        StaffIdentitySecurityEmail notice = new StaffIdentitySecurityEmail(
                "staff@example.test", "Staff", IdentitySecurityNoticeType.ACCOUNT_SUSPENDED,
                "", "", Instant.parse("2026-09-21T12:00:00Z"));

        assertThat(adapter.sendSecurityNotice(notice)).isEqualTo(IdentityEmailDeliveryStatus.FAILED);
    }

    @Test
    void composesBranchAssignmentNoticeWithoutExposingBranchOrActorDetails() {
        when(emailSender.send(any())).thenReturn(EmailSendResult.sent());
        StaffIdentitySecurityEmailAdapter adapter = new StaffIdentitySecurityEmailAdapter(
                emailSender, properties());
        StaffIdentitySecurityEmail notice = new StaffIdentitySecurityEmail(
                "staff@example.test", "Staff", IdentitySecurityNoticeType.BRANCH_ASSIGNMENTS_CHANGED,
                "", "", Instant.parse("2026-09-21T12:00:00Z"));

        assertThat(adapter.sendSecurityNotice(notice)).isEqualTo(IdentityEmailDeliveryStatus.SENT);

        ArgumentCaptor<EmailMessage> message = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender).send(message.capture());
        assertThat(message.getValue().plainTextBody())
                .contains("your staff branch assignments changed")
                .doesNotContain("branch id", "actor", "reason", "password");
        assertThat(message.getValue().htmlBody())
                .contains("your staff branch assignments changed")
                .doesNotContain("branch id", "actor", "reason", "password");
    }

    private static EmailProperties properties() {
        return new EmailProperties(
                false, "Coach Gym", "v1", "no-reply@example.test", "Coach Gym", null,
                100_000, 200, 3, Duration.ofMinutes(15));
    }
}
