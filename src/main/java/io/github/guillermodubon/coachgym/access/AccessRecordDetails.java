package io.github.guillermodubon.coachgym.access;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable projection of a persisted access record.
 *
 * <p>Fields that depend on successful identifier resolution
 * ({@code clientId}, {@code clientCode}, {@code membershipId},
 * {@code membershipCode}) are nullable: they are absent when the presented
 * identifier could not be resolved.</p>
 *
 * <p>{@code processedByUserId} is nullable because the recording user may be
 * deleted after the attempt is persisted (ON DELETE SET NULL on the FK).</p>
 */
public record AccessRecordDetails(
        UUID id,
        String presentedIdentifier,
        UUID clientId,
        String clientCode,
        UUID membershipId,
        String membershipCode,
        AccessResult result,
        AccessReasonCode reasonCode,
        String reason,
        Instant checkedInAt,
        UUID processedByUserId) {
}
