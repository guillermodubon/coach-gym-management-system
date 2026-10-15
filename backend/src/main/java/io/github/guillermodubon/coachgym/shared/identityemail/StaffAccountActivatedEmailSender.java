package io.github.guillermodubon.coachgym.shared.identityemail;

/** Provider-neutral port for one best-effort post-commit account-activated notice. */
public interface StaffAccountActivatedEmailSender {

    IdentityEmailDeliveryStatus sendAccountActivated(StaffAccountActivatedEmail email);
}
