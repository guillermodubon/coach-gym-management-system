package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.shared.identityemail.StaffAccountActivatedEmail;
import java.util.Objects;
import java.util.UUID;

/** One leased, transient activation-notice attempt; the recipient is redacted from diagnostics. */
public record StaffAccountActivationDeliveryClaim(
        UUID deliveryId,
        int attemptNumber,
        StaffAccountActivatedEmail email) {

    public StaffAccountActivationDeliveryClaim {
        deliveryId = Objects.requireNonNull(deliveryId);
        if (attemptNumber < 1 || attemptNumber > StaffAccountActivationRetryPolicy.MAX_ATTEMPTS) {
            throw new IllegalArgumentException("Activation delivery attempt is outside its bounds.");
        }
        email = Objects.requireNonNull(email);
    }

    @Override
    public String toString() {
        return "StaffAccountActivationDeliveryClaim[deliveryId=" + deliveryId
                + ", attemptNumber=" + attemptNumber
                + ", emailPresent=" + (email != null) + ']';
    }
}
