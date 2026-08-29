package io.github.guillermodubon.coachgym.membership;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Minimal membership projection required by the access check-in use case.
 *
 * <p>Includes the current period boundaries and the open freeze window (if any)
 * so the access policy can evaluate period validity and freeze status without
 * accessing membership infrastructure directly.</p>
 *
 * <p>Freeze fields are {@code null} when no open freeze exists for the
 * membership. The access policy treats a null freeze window as "no active
 * freeze", regardless of {@link MembershipStatus}.</p>
 */
public record MembershipAccessDetails(
        UUID membershipId,
        String membershipCode,
        UUID clientId,
        MembershipStatus status,
        UUID currentPeriodId,
        LocalDate periodStartsOn,
        LocalDate periodEffectiveEndsOn,
        LocalDate freezeStartsOn,
        LocalDate freezePlannedEndsOn) {
}
