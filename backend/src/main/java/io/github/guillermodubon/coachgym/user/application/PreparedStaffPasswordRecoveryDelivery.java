package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.shared.identityemail.StaffPasswordRecoveryEmail;
import java.util.Objects;

/** Internal ephemeral post-commit handoff; diagnostic output never includes the email or token. */
record PreparedStaffPasswordRecoveryDelivery(StaffPasswordRecoveryEmail email) {

    PreparedStaffPasswordRecoveryDelivery {
        Objects.requireNonNull(email);
    }

    @Override
    public String toString() {
        return "PreparedStaffPasswordRecoveryDelivery[messagePrepared=true]";
    }
}
