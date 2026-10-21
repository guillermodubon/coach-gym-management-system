package io.github.guillermodubon.coachgym.membership.web;

import io.github.guillermodubon.coachgym.membership.MembershipDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPeriodCoverageSummary;
import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "MembershipResponse", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record MembershipResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String membershipCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID clientId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        MembershipStatus status,
        @Schema(description = "Current period snapshot, or null when the membership has no current period.",
                nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        MembershipPeriodResponse currentPeriod,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID registeredAtBranchId) {

    static MembershipResponse from(
            MembershipDetails membership) {
        return from(membership, null);
    }

    static MembershipResponse from(
            MembershipDetails membership,
            MembershipPeriodCoverageSummary coverage) {

        return new MembershipResponse(
                membership.id(),
                membership.membershipCode(),
                membership.clientId(),
                membership.status(),
                MembershipPeriodResponse.from(
                        membership.currentPeriod(), coverage),
                membership.createdAt(),
                membership.updatedAt(),
                membership.version(),
                membership.registeredAtBranchId());
    }
}
