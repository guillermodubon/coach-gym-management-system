package io.github.guillermodubon.coachgym.payment.application;

import java.util.UUID;

/** Staff request to start a card checkout for an existing membership period. */
public record CreateCardCheckoutAttemptCommand(
        UUID clientId,
        UUID membershipId,
        UUID membershipPeriodId) {

    public CreateCardCheckoutAttemptCommand {
        clientId = PaymentAttemptCommandValidation.requiredIdentifier(clientId, "Client id");
        membershipId = PaymentAttemptCommandValidation.requiredIdentifier(membershipId, "Membership id");
        membershipPeriodId = PaymentAttemptCommandValidation.requiredIdentifier(
                membershipPeriodId, "Membership period id");
    }
}
