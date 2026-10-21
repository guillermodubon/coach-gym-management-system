package io.github.guillermodubon.coachgym.membership.web;

import io.github.guillermodubon.coachgym.membership.domain.MembershipPricingSnapshot;
import io.github.guillermodubon.coachgym.plan.DurationUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(name = "MembershipPricingResponse", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record MembershipPricingResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID membershipPlanId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String planCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String planName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int durationValue,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        DurationUnit durationUnit,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal listPrice,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String currency,
        @Schema(description = "Promotion snapshot, or null when the period was not purchased with a promotion.",
                nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        MembershipPromotionResponse promotion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal discountAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal finalPrice) {

    static MembershipPricingResponse from(
            MembershipPricingSnapshot pricing) {

        return new MembershipPricingResponse(
                pricing.membershipPlanId(),
                pricing.planCode(),
                pricing.planName(),
                pricing.durationValue(),
                pricing.durationUnit(),
                pricing.listPrice(),
                pricing.currency(),
                MembershipPromotionResponse.from(
                        pricing.promotion()),
                pricing.discountAmount(),
                pricing.finalPrice());
    }
}
