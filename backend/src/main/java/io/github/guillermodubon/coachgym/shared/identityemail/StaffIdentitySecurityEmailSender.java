package io.github.guillermodubon.coachgym.shared.identityemail;

/** Provider-neutral boundary for best-effort post-commit staff security notices. */
public interface StaffIdentitySecurityEmailSender {

    IdentityEmailDeliveryStatus sendSecurityNotice(StaffIdentitySecurityEmail email);
}
