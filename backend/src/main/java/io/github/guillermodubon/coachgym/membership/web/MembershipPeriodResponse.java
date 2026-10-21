package io.github.guillermodubon.coachgym.membership.web;

import io.github.guillermodubon.coachgym.membership.MembershipPeriodDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPeriodCoverageSummary;
import io.github.guillermodubon.coachgym.membership.MembershipPeriodSource;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(name = "MembershipPeriodResponse", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record MembershipPeriodResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        short periodNumber,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        MembershipPeriodSource source,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        MembershipPricingResponse pricing,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate startsOn,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate baseEndsOn,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate effectiveEndsOn,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID registeredAtBranchId,
        @Schema(description = "Immutable branch coverage summary, or null if no snapshot exists for this period.",
                nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        MembershipPeriodCoverageResponse coverage) {

    static MembershipPeriodResponse from(
            MembershipPeriodDetails period) {
        return from(period, null);
    }

    static MembershipPeriodResponse from(
            MembershipPeriodDetails period,
            MembershipPeriodCoverageSummary coverage) {

        return new MembershipPeriodResponse(
                period.id(),
                period.periodNumber(),
                period.source(),
                MembershipPricingResponse.from(
                        period.pricing()),
                period.startsOn(),
                period.baseEndsOn(),
                period.effectiveEndsOn(),
                period.createdAt(),
                period.version(),
                period.registeredAtBranchId(),
                MembershipPeriodCoverageResponse.from(coverage));
    }
}
