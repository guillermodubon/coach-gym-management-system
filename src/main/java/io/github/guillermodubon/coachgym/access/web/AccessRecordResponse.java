package io.github.guillermodubon.coachgym.access.web;

import io.github.guillermodubon.coachgym.access.AccessReasonCode;
import io.github.guillermodubon.coachgym.access.AccessRecordDetails;
import io.github.guillermodubon.coachgym.access.AccessResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * HTTP representation of a persisted access attempt.
 *
 * <p>Resolved client and membership values are nullable because unknown
 * identifiers and clients without memberships are normal denied business
 * decisions.</p>
 */
public record AccessRecordResponse(
        UUID id,
        String presentedIdentifier,
        UUID clientId,
        String clientCode,
        UUID membershipId,
        String membershipCode,
        AccessResult result,

        @Schema(description = "Stable, non-sensitive business decision code. A DENIED response is a recorded decision, not an HTTP authorization failure.")
        AccessReasonCode reasonCode,

        @Schema(description = "Safe, server-generated explanation for the business result; never contains SQL, secrets, or internal exception details.")
        String reason,
        Instant checkedInAt,
        UUID processedByUserId,
        UUID branchId) {

    public static AccessRecordResponse from(
            AccessRecordDetails details) {

        if (details == null) {
            throw new IllegalArgumentException(
                    "Access record details must be provided.");
        }

        return new AccessRecordResponse(
                details.id(),
                details.presentedIdentifier(),
                details.clientId(),
                details.clientCode(),
                details.membershipId(),
                details.membershipCode(),
                details.result(),
                details.reasonCode(),
                details.reason(),
                details.checkedInAt(),
                details.processedByUserId(),
                details.branchId());
    }
}

