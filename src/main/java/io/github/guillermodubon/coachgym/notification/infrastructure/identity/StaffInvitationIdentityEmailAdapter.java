package io.github.guillermodubon.coachgym.notification.infrastructure.identity;

import io.github.guillermodubon.coachgym.notification.EmailMessage;
import io.github.guillermodubon.coachgym.notification.EmailSendResult;
import io.github.guillermodubon.coachgym.notification.application.EmailSender;
import io.github.guillermodubon.coachgym.notification.infrastructure.smtp.EmailProperties;
import io.github.guillermodubon.coachgym.notification.infrastructure.smtp.IdentityEmailProperties;
import io.github.guillermodubon.coachgym.shared.identityemail.IdentityEmailDeliveryStatus;
import io.github.guillermodubon.coachgym.shared.identityemail.StaffInvitationEmail;
import io.github.guillermodubon.coachgym.shared.identityemail.StaffInvitationEmailSender;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Provider-neutral adapter for ephemeral staff invitation messages. */
@Component
public final class StaffInvitationIdentityEmailAdapter implements StaffInvitationEmailSender {

    private final EmailSender emailSender;
    private final EmailProperties emailProperties;
    private final IdentityEmailProperties identityEmailProperties;

    public StaffInvitationIdentityEmailAdapter(
            EmailSender emailSender,
            EmailProperties emailProperties,
            IdentityEmailProperties identityEmailProperties) {
        this.emailSender = Objects.requireNonNull(emailSender);
        this.emailProperties = Objects.requireNonNull(emailProperties);
        this.identityEmailProperties = Objects.requireNonNull(identityEmailProperties);
    }

    @Override
    public IdentityEmailDeliveryStatus sendInvitation(StaffInvitationEmail invitation) {
        Objects.requireNonNull(invitation, "Invitation email is required.");
        String invitationUrl = identityEmailProperties.invitationAcceptanceUrl()
                + "#token=" + invitation.token();
        String role = escapeHtml(invitation.roleLabel());
        String scope = escapeHtml(invitation.scopeLabel());
        String branches = invitation.branchNames().isEmpty()
                ? ""
                : "\nBranches: " + String.join(", ", invitation.branchNames());
        String expiration = DateTimeFormatter.ISO_INSTANT.format(invitation.expiresAt());
        String plainText = "You have been invited to " + emailProperties.organizationName()
                + " as " + invitation.roleLabel() + " (" + invitation.scopeLabel() + ")."
                + branches
                + "\nAccept the invitation: " + invitationUrl
                + "\nThis invitation expires at " + expiration + ".";
        String html = "<p>You have been invited to <strong>"
                + escapeHtml(emailProperties.organizationName())
                + "</strong> as <strong>" + role + "</strong> (" + scope + ").</p>"
                + (invitation.branchNames().isEmpty() ? "" : "<p>Branches: "
                        + invitation.branchNames().stream().map(StaffInvitationIdentityEmailAdapter::escapeHtml)
                                .collect(java.util.stream.Collectors.joining(", "))
                        + "</p>")
                + "<p><a href=\"" + escapeHtml(invitationUrl)
                + "\">Accept invitation</a></p><p>This invitation expires at "
                + escapeHtml(expiration) + ".</p>";
        EmailSendResult result = emailSender.send(new EmailMessage(
                invitation.recipient(),
                emailProperties.fromAddress(),
                emailProperties.fromName(),
                emailProperties.replyTo(),
                "Staff invitation",
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
