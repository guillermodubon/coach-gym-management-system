package io.github.guillermodubon.coachgym.membership;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MembershipFreezeDetails(
        UUID id,
        UUID membershipId,
        UUID membershipPeriodId,
        LocalDate startsOn,
        LocalDate plannedEndsOn,
        String reason,
        LocalDate reactivatedOn,
        UUID createdByUserId,
        UUID reactivatedByUserId,
        LocalDate cancelledOn,
        UUID cancelledByUserId,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public boolean open() {
        return reactivatedOn == null
                && cancelledOn == null;
    }

    public boolean reactivated() {
        return reactivatedOn != null;
    }

    public boolean closedByCancellation() {
        return cancelledOn != null;
    }
}
