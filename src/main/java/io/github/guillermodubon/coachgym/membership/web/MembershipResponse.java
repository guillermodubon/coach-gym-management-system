package io.github.guillermodubon.coachgym.membership.web;

import io.github.guillermodubon.coachgym.membership.MembershipDetails;
import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import java.time.Instant;
import java.util.UUID;

public record MembershipResponse(
        UUID id,
        String membershipCode,
        UUID clientId,
        MembershipStatus status,
        MembershipPeriodResponse currentPeriod,
        Instant createdAt,
        Instant updatedAt,
        long version,
        UUID registeredAtBranchId) {

    static MembershipResponse from(
            MembershipDetails membership) {

        return new MembershipResponse(
                membership.id(),
                membership.membershipCode(),
                membership.clientId(),
                membership.status(),
                MembershipPeriodResponse.from(
                        membership.currentPeriod()),
                membership.createdAt(),
                membership.updatedAt(),
                membership.version(),
                membership.registeredAtBranchId());
    }
}
