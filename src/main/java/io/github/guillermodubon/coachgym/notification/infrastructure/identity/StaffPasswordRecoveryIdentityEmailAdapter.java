package io.github.guillermodubon.coachgym.notification.infrastructure.identity;

import io.github.guillermodubon.coachgym.notification.EmailMessage;
import io.github.guillermodubon.coachgym.notification.EmailSendResult;
import io.github.guillermodubon.coachgym.notification.EmailAttemptResult;
import io.github.guillermodubon.coachgym.notification.application.EmailSender;
import io.github.guillermodubon.coachgym.notification.infrastructure.smtp.EmailProperties;
import io.github.guillermodubon.coachgym.notification.infrastructure.smtp.IdentityEmailProperties;
import io.github.guillermodubon.coachgym.shared.identityemail.StaffPasswordRecoveryEmail;
import io.github.guillermodubon.coachgym.shared.identityemail.StaffPasswordRecoveryEmailSender;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Sends recovery links asynchronously and ephemerally without a durable token-bearing outbox. */
@Component
public class StaffPasswordRecoveryIdentityEmailAdapter implements StaffPasswordRecoveryEmailSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            StaffPasswordRecoveryIdentityEmailAdapter.class);

    private final EmailSender emailSender;
    private final EmailProperties emailProperties;
    private final IdentityEmailProperties identityEmailProperties;

    public StaffPasswordRecoveryIdentityEmailAdapter(
            EmailSender emailSender,
            EmailProperties emailProperties,
            IdentityEmailProperties identityEmailProperties) {
        this.emailSender = Objects.requireNonNull(emailSender);
        this.emailProperties = Objects.requireNonNull(emailProperties);
        this.identityEmailProperties = Objects.requireNonNull(identityEmailProperties);
    }

    @Override
    @Async("identityEmailTaskExecutor")
    public void sendPasswordRecoveryLink(StaffPasswordRecoveryEmail recovery) {
        Objects.requireNonNull(recovery, "Password recovery email is required.");
        String link = identityEmailProperties.passwordRecoveryUrl()
                + "#token=" + URLEncoder.encode(recovery.token(), StandardCharsets.UTF_8);
        String expiration = DateTimeFormatter.ISO_INSTANT.format(recovery.expiresAt());
        String plainText = "Hello " + recovery.displayName() + ", use this one-time link to reset your "
                + emailProperties.organizationName() + " staff password: " + link
                + "\nThis link expires at " + expiration
                + ". If you did not request it, you can ignore this message.";
        String html = "<p>Hello <strong>" + escapeHtml(recovery.displayName())
                + "</strong>, use this one-time link to reset your "
                + escapeHtml(emailProperties.organizationName()) + " staff password.</p>"
                + "<p><a href=\"" + escapeHtml(link) + "\">Reset password</a></p>"
                + "<p>This link expires at " + escapeHtml(expiration)
                + ". If you did not request it, you can ignore this message.</p>";
        try {
            EmailSendResult result = emailSender.send(new EmailMessage(
                    recovery.recipient(),
                    emailProperties.fromAddress(),
                    emailProperties.fromName(),
                    emailProperties.replyTo(),
                    "Password recovery",
                    plainText,
                    html,
                    null));
            if (result == null || result.result() != EmailAttemptResult.SENT) {
                LOGGER.warn("Password recovery email delivery was not confirmed.");
            }
        } catch (RuntimeException failure) {
            LOGGER.warn("Password recovery email delivery failed.");
        }
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
