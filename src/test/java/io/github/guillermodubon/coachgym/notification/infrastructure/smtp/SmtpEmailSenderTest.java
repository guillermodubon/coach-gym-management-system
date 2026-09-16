package io.github.guillermodubon.coachgym.notification.infrastructure.smtp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.notification.EmailAttachment;
import io.github.guillermodubon.coachgym.notification.EmailAttemptResult;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryFailureCode;
import io.github.guillermodubon.coachgym.notification.EmailMessage;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.net.SocketTimeoutException;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpEmailSenderTest {

    @Test
    void sendsMultipartAlternativeWithCanonicalAttachment() throws Exception {
        JavaMailSender delegate = mock(JavaMailSender.class);
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        when(delegate.createMimeMessage()).thenReturn(mime);
        EmailMessage message = message();

        var result = new SmtpEmailSender(delegate, properties(true)).send(message);

        assertThat(result.result().name()).isEqualTo("SENT");
        verify(delegate).send(mime);
        mime.saveChanges();
        assertThat(mime.getHeader("To", null)).contains("ana@example.com");
        assertThat(mime.getSubject()).isEqualTo("Coach Gym receipt");
        assertThat(mime.getContent()).isInstanceOf(Multipart.class);
        Multipart mixed = (Multipart) mime.getContent();
        assertThat(mixed.getCount()).isEqualTo(2);
        assertThat(mixed.getBodyPart(0).getContent()).isInstanceOf(Multipart.class);
        Multipart related = (Multipart) mixed.getBodyPart(0).getContent();
        assertThat(related.getCount()).isEqualTo(1);
        Multipart alternatives = (Multipart) related.getBodyPart(0).getContent();
        assertThat(alternatives.getCount()).isEqualTo(2);
        assertThat(alternatives.getBodyPart(0).getContent()).isEqualTo("Plain text body");
        assertThat((String) alternatives.getBodyPart(1).getContent()).contains("HTML body");
        assertThat(mixed.getBodyPart(1).getFileName()).isEqualTo("receipt.pdf");
        assertThat(mixed.getBodyPart(1).getContentType()).startsWith("application/pdf");
        assertThat(mixed.getBodyPart(1).getInputStream().readAllBytes())
                .containsExactly(1, 2, 3);
    }

    @Test
    void mapsDisabledOversizedAuthenticationTimeoutAndRejectionSafely() throws Exception {
        JavaMailSender delegate = mock(JavaMailSender.class);
        SmtpEmailSender disabled = new SmtpEmailSender(delegate, properties(false));
        assertThat(disabled.send(message()).failureCode())
                .isEqualTo(EmailDeliveryFailureCode.DELIVERY_DISABLED);
        verifyNoInteractions(delegate);

        JavaMailSender authDelegate = mock(JavaMailSender.class);
        when(authDelegate.createMimeMessage()).thenThrow(new MailAuthenticationException("secret"));
        assertThat(new SmtpEmailSender(authDelegate, properties(true)).send(message()))
                .satisfies(result -> {
                    assertThat(result.failureCode()).isEqualTo(
                            EmailDeliveryFailureCode.TRANSPORT_AUTHENTICATION_FAILED);
                    assertThat(result.failureMessage()).doesNotContain("secret");
                });

        JavaMailSender timeoutDelegate = mock(JavaMailSender.class);
        when(timeoutDelegate.createMimeMessage()).thenReturn(
                new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("send", new SocketTimeoutException("secret")))
                .when(timeoutDelegate).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
        assertThat(new SmtpEmailSender(timeoutDelegate, properties(true)).send(message()).failureCode())
                .isEqualTo(EmailDeliveryFailureCode.TRANSPORT_TIMEOUT);

        JavaMailSender rejectedDelegate = mock(JavaMailSender.class);
        when(rejectedDelegate.createMimeMessage()).thenThrow(new MailSendException("rejected"));
        assertThat(new SmtpEmailSender(rejectedDelegate, properties(true)).send(message()).failureCode())
                .isEqualTo(EmailDeliveryFailureCode.TRANSPORT_REJECTED);
    }

    @Test
    void classifiesRecipientLevelSendFailureAsAmbiguousWithoutProviderDetails() throws Exception {
        JavaMailSender delegate = mock(JavaMailSender.class);
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        when(delegate.createMimeMessage()).thenReturn(mime);
        doThrow(new MailSendException(
                "transport outcome unknown",
                null,
                Map.<Object, Exception>of(mime, new IOException("smtp secret"))))
                .when(delegate).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));

        assertThat(new SmtpEmailSender(delegate, properties(true)).send(message()))
                .satisfies(result -> {
                    assertThat(result.result()).isEqualTo(EmailAttemptResult.AMBIGUOUS);
                    assertThat(result.failureCode())
                            .isEqualTo(EmailDeliveryFailureCode.AMBIGUOUS_TRANSPORT_OUTCOME);
                    assertThat(result.failureMessage()).doesNotContain("smtp secret");
                });
    }

    @Test
    void rejectsConfiguredMessageOverflowBeforeTransport() {
        JavaMailSender delegate = mock(JavaMailSender.class);
        EmailProperties limited = new EmailProperties(true, "Coach Gym", "v1",
                "no-reply@coach-gym.local", "Coach Gym", null, "localhost", 1025, null, null,
                false, false, Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(10),
                1_024, 10 * 1024 * 1024, 200, 254);
        assertThat(new SmtpEmailSender(delegate, limited).send(message()).failureCode())
                .isEqualTo(EmailDeliveryFailureCode.VALIDATION_FAILED);
        verifyNoInteractions(delegate);
    }

    private static EmailMessage message() {
        return new EmailMessage("ana@example.com", "no-reply@coach-gym.local", "Coach Gym", null,
                "Coach Gym receipt", "Plain text body", "<p>HTML body</p>",
                new EmailAttachment("receipt.pdf", "application/pdf", new byte[]{1, 2, 3}));
    }

    private static EmailProperties properties(boolean enabled) {
        return new EmailProperties(enabled, "Coach Gym", "v1", "no-reply@coach-gym.local",
                "Coach Gym", null, "localhost", 1025, null, null, false, false,
                Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(10),
                12 * 1024 * 1024, 10 * 1024 * 1024, 200, 254);
    }
}
