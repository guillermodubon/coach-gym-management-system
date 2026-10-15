package io.github.guillermodubon.coachgym.notification.infrastructure.identity;

import io.github.guillermodubon.coachgym.notification.EmailMessage;
import io.github.guillermodubon.coachgym.notification.EmailSendResult;
import io.github.guillermodubon.coachgym.notification.application.EmailSender;
import io.github.guillermodubon.coachgym.notification.infrastructure.email.EmailProperties;
import io.github.guillermodubon.coachgym.shared.identityemail.IdentityEmailDeliveryStatus;
import io.github.guillermodubon.coachgym.shared.identityemail.IdentitySecurityNoticeType;
import io.github.guillermodubon.coachgym.shared.identityemail.StaffIdentitySecurityEmail;
import io.github.guillermodubon.coachgym.shared.identityemail.StaffIdentitySecurityEmailSender;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Sends token-free, provider-neutral security notices through notification-owned transport. */
@Component
public final class StaffIdentitySecurityEmailAdapter implements StaffIdentitySecurityEmailSender {

    private final EmailSender emailSender;
    private final EmailProperties properties;

    public StaffIdentitySecurityEmailAdapter(EmailSender emailSender, EmailProperties properties) {
        this.emailSender = Objects.requireNonNull(emailSender);
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    public IdentityEmailDeliveryStatus sendSecurityNotice(StaffIdentitySecurityEmail notice) {
        Objects.requireNonNull(notice, "Identity security notice is required.");
        String displayName = escapeHtml(notice.displayName());
        String organization = escapeHtml(properties.organizationName());
        String message = messageFor(notice);
        String occurredAt = DateTimeFormatter.ISO_INSTANT.format(notice.occurredAt());
        String plainText = "Hello " + notice.displayName() + ", " + message + " for "
                + properties.organizationName() + " at " + occurredAt
                + ". If you did not expect this change, contact your organization administrator.";
        String html = "<p>Hello <strong>" + displayName + "</strong>, "
                + escapeHtml(message) + " for <strong>" + organization + "</strong> at "
                + escapeHtml(occurredAt)
                + ". If you did not expect this change, contact your organization administrator.</p>";
        EmailSendResult result = emailSender.send(new EmailMessage(
                notice.recipient(), properties.fromAddress(), properties.fromName(),
                properties.replyTo(), "Staff account security update", plainText, html, null));
        if (result == null) {
            return IdentityEmailDeliveryStatus.AMBIGUOUS;
        }
        return switch (result.result()) {
            case SENT -> IdentityEmailDeliveryStatus.SENT;
            case FAILED -> IdentityEmailDeliveryStatus.FAILED;
            case AMBIGUOUS -> IdentityEmailDeliveryStatus.AMBIGUOUS;
        };
    }

    private static String messageFor(StaffIdentitySecurityEmail notice) {
        return switch (notice.noticeType()) {
            case ACCOUNT_SUSPENDED -> "your staff account was suspended";
            case ACCOUNT_REACTIVATED -> "your staff account was reactivated";
            case ACCOUNT_DEACTIVATED -> "your staff account was deactivated";
            case PASSWORD_CHANGED -> "your staff password was changed";
            case AUTHORITY_CHANGED -> "your staff role/scope changed to "
                    + notice.roleLabel() + " / " + notice.scopeLabel();
            case BRANCH_ASSIGNMENTS_CHANGED -> "your staff branch assignments changed";
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
