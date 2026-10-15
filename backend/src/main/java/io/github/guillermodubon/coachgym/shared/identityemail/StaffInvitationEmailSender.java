package io.github.guillermodubon.coachgym.shared.identityemail;

/** Ephemeral, provider-neutral identity-email delivery boundary. */
public interface StaffInvitationEmailSender {

    IdentityEmailDeliveryStatus sendInvitation(StaffInvitationEmail email);
}
