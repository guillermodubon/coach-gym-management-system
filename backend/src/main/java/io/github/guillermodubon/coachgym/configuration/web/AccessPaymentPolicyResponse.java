package io.github.guillermodubon.coachgym.configuration.web;

import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Safe HTTP projection of the persisted access-payment policy setting. */
@Schema(
        name = "AccessPaymentPolicyResponse",
        description = "Current access-payment policy and server-owned optimistic-lock metadata.")
record AccessPaymentPolicyResponse(
        @Schema(description = "Whether a confirmed PAID payment is required for manual and QR access.")
        boolean requireConfirmedPaymentForAccess,
        @Schema(description = "Current optimistic-lock version.", minimum = "0", example = "1")
        long version,
        @Schema(description = "Server timestamp maintained for the settings row.")
        Instant updatedAt,
        @Schema(description = "Server actor that performed the last policy change. Null before the first change.")
        UUID updatedByUserId) {

    static AccessPaymentPolicyResponse from(AccessPaymentPolicyDetails details) {
        return new AccessPaymentPolicyResponse(
                details.requireConfirmedPaymentForAccess(),
                details.version(),
                details.updatedAt(),
                details.updatedByUserId());
    }
}
