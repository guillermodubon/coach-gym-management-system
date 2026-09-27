package io.github.guillermodubon.coachgym.notification.infrastructure.identity;

import io.github.guillermodubon.coachgym.notification.EmailMessage;
import io.github.guillermodubon.coachgym.notification.EmailSendResult;
import io.github.guillermodubon.coachgym.notification.application.EmailSender;
import io.github.guillermodubon.coachgym.notification.infrastructure.smtp.EmailProperties;
import io.github.guillermodubon.coachgym.shared.identityemail.IdentityEmailDeliveryStatus;
import io.github.guillermodubon.coachgym.shared.identityemail.StaffAccountActivatedEmail;
import io.github.guillermodubon.coachgym.shared.identityemail.StaffAccountActivatedEmailSender;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Provider-neutral adapter for the token-free account-activated security notice. */
@Component
public final class StaffAccountActivatedIdentityEmailAdapter
        implements StaffAccountActivatedEmailSender {

    private final EmailSender emailSender;
    private final EmailProperties emailProperties;

    public StaffAccountActivatedIdentityEmailAdapter(
            EmailSender emailSender,
            EmailProperties emailProperties) {
        this.emailSender = Objects.requireNonNull(emailSender);
        this.emailProperties = Objects.requireNonNull(emailProperties);
    }

    @Override
    public IdentityEmailDeliveryStatus sendAccountActivated(StaffAccountActivatedEmail activation) {
        Objects.requireNonNull(activation, "Activation email is required.");
        String organization = escapeHtml(emailProperties.organizationName());
        String name = escapeHtml(activation.displayName());
        String role = escapeHtml(activation.roleLabel());
        String scope = escapeHtml(activation.scopeLabel());
        String occurredAt = DateTimeFormatter.ISO_INSTANT.format(activation.activatedAt());
        String plainText = "Hello " + activation.displayName() + ", your " + organization
                + " staff account is now active as " + activation.roleLabel()
                + " (" + activation.scopeLabel() + ").\nActivated at " + occurredAt
                + ". If you did not accept this invitation, contact your organization administrator.";
        String html = "<p>Hello <strong>" + name + "</strong>, your <strong>"
                + organization + "</strong> staff account is now active as <strong>"
                + role + "</strong> (" + scope + ").</p><p>Activated at "
                + escapeHtml(occurredAt)
                + ". If you did not accept this invitation, contact your organization administrator.</p>";
        EmailSendResult result = emailSender.send(new EmailMessage(
                activation.recipient(),
                emailProperties.fromAddress(),
                emailProperties.fromName(),
                emailProperties.replyTo(),
                "Staff account activated",
                plainText,
                html,
                null));
        if (result == null) {
            return IdentityEmailDeliveryStatus.AMBIGUOUS;
        }
        return switch (result.result()) {
            case SENT -> IdentityEmailDeliveryStatus.SENT;
            case FAILED -> IdentityEmailDeliveryStatus.FAILED;
            case AMBIGUOUS -> IdentityEmailDeliveryStatus.AMBIGUOUS;
        };
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
