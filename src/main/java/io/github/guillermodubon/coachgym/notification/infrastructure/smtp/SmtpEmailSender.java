package io.github.guillermodubon.coachgym.notification.infrastructure.smtp;

import io.github.guillermodubon.coachgym.notification.EmailDeliveryFailureCode;
import io.github.guillermodubon.coachgym.notification.EmailMessage;
import io.github.guillermodubon.coachgym.notification.EmailSendResult;
import io.github.guillermodubon.coachgym.notification.application.EmailSender;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.io.UnsupportedEncodingException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/** Generic SMTP adapter; Jakarta Mail remains confined to this infrastructure package. */
final class SmtpEmailSender implements EmailSender {

    private static final long MIME_OVERHEAD_BYTES = 8 * 1024L;

    private final JavaMailSender mailSender;
    private final EmailProperties properties;

    SmtpEmailSender(JavaMailSender mailSender, EmailProperties properties) {
        this.mailSender = Objects.requireNonNull(mailSender, "JavaMail sender is required.");
        this.properties = Objects.requireNonNull(properties, "Email properties are required.");
    }

    @Override
    public EmailSendResult send(EmailMessage message) {
        if (message == null) {
            return EmailSendResult.failed(
                    EmailDeliveryFailureCode.VALIDATION_FAILED,
                    "Email message is required.");
        }
        if (!properties.enabled()) {
            return EmailSendResult.failed(
                    EmailDeliveryFailureCode.DELIVERY_DISABLED,
                    "Transactional email delivery is disabled.");
        }
        if (!properties.isValidWhenEnabled()) {
            return EmailSendResult.failed(
                    EmailDeliveryFailureCode.TRANSPORT_CONFIGURATION_FAILED,
                    "Transactional email configuration is invalid.");
        }
        if (estimatedSize(message) > properties.maxMessageBytes()) {
            return EmailSendResult.failed(
                    EmailDeliveryFailureCode.VALIDATION_FAILED,
                    "Email message exceeds the configured size limit.");
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage, true, StandardCharsets.UTF_8.name());
            if (message.fromName() == null) {
                helper.setFrom(message.fromAddress());
            } else {
                helper.setFrom(message.fromAddress(), message.fromName());
            }
            helper.setTo(message.recipient());
            if (message.replyTo() != null) {
                helper.setReplyTo(message.replyTo());
            }
            helper.setSubject(message.subject());
            helper.setText(message.plainTextBody(), message.htmlBody());
            helper.addAttachment(
                    message.attachment().filename(),
                    new ByteArrayResource(message.attachment().bytes()),
                    message.attachment().contentType());
            mailSender.send(mimeMessage);
            return EmailSendResult.sent();
        } catch (MailAuthenticationException exception) {
            return EmailSendResult.failed(
                    EmailDeliveryFailureCode.TRANSPORT_AUTHENTICATION_FAILED,
                    "SMTP authentication failed.");
        } catch (MailException exception) {
            return classify(exception);
        } catch (MessagingException | UnsupportedEncodingException | IllegalArgumentException exception) {
            return EmailSendResult.failed(
                    EmailDeliveryFailureCode.TRANSPORT_CONFIGURATION_FAILED,
                    "The email message could not be prepared for SMTP delivery.");
        } catch (RuntimeException exception) {
            return EmailSendResult.failed(
                    EmailDeliveryFailureCode.UNEXPECTED_FAILURE,
                    "The email could not be delivered.");
        }
    }

    private static EmailSendResult classify(MailException exception) {
        if (hasCause(exception, MailAuthenticationException.class)) {
            return EmailSendResult.failed(
                    EmailDeliveryFailureCode.TRANSPORT_AUTHENTICATION_FAILED,
                    "SMTP authentication failed.");
        }
        if (hasCause(exception, SocketTimeoutException.class)) {
            return EmailSendResult.failed(
                    EmailDeliveryFailureCode.TRANSPORT_TIMEOUT,
                    "SMTP delivery timed out.");
        }
        if (hasCause(exception, ConnectException.class)
                || hasCause(exception, java.net.UnknownHostException.class)) {
            return EmailSendResult.failed(
                    EmailDeliveryFailureCode.TRANSPORT_CONFIGURATION_FAILED,
                    "The SMTP server could not be reached.");
        }
        if (exception instanceof org.springframework.mail.MailSendException sendException
                && sendException.getFailedMessages() != null
                && !sendException.getFailedMessages().isEmpty()) {
            // JavaMail may have accepted a message before reporting a
            // recipient-level failure. The provider-neutral boundary must
            // preserve that uncertainty instead of enabling a blind resend.
            return EmailSendResult.ambiguous(
                    "The SMTP transport outcome could not be confirmed.");
        }
        if (hasCause(exception, MessagingException.class)
                || exception.getClass().getSimpleName().contains("Send")) {
            return EmailSendResult.failed(
                    EmailDeliveryFailureCode.TRANSPORT_REJECTED,
                    "The SMTP server rejected the email.");
        }
        return EmailSendResult.failed(
                EmailDeliveryFailureCode.UNEXPECTED_FAILURE,
                "The email could not be delivered.");
    }

    private static long estimatedSize(EmailMessage message) {
        long attachmentBytes = message.attachment().sizeBytes();
        long encodedAttachmentBytes = attachmentBytes > (Long.MAX_VALUE / 4) * 3
                ? Long.MAX_VALUE
                : ((attachmentBytes + 2) / 3) * 4;
        long size = MIME_OVERHEAD_BYTES
                + utf8Length(message.subject())
                + utf8Length(message.plainTextBody())
                + utf8Length(message.htmlBody())
                + encodedAttachmentBytes;
        return size < 0 ? Long.MAX_VALUE : size;
    }

    private static long utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
