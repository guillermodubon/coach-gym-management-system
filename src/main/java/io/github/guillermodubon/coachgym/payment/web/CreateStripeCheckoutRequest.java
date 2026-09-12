package io.github.guillermodubon.coachgym.payment.web;

import io.github.guillermodubon.coachgym.payment.application.CreateCardCheckoutAttemptCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Internal membership selection used to create a server-priced Stripe checkout. */
@Schema(description = "Existing membership period selected for a Stripe Test Mode checkout.")
record CreateStripeCheckoutRequest(

        @NotNull
        @Schema(description = "Client that owns the membership.", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID clientId,

        @NotNull
        @Schema(description = "Existing membership selected for payment.", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID membershipId,

        @NotNull
        @Schema(description = "Membership period whose server-side price is charged.", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID membershipPeriodId) {

    CreateCardCheckoutAttemptCommand toCommand() {
        return new CreateCardCheckoutAttemptCommand(clientId, membershipId, membershipPeriodId);
    }
}
