package io.github.guillermodubon.coachgym.membership.web;

import io.github.guillermodubon.coachgym.membership.domain.MembershipPricingSnapshot;
import io.github.guillermodubon.coachgym.plan.DurationUnit;
import java.math.BigDecimal;
import java.util.UUID;

public record MembershipPricingResponse(
        UUID membershipPlanId,
        String planCode,
        String planName,
        int durationValue,
        DurationUnit durationUnit,
        BigDecimal listPrice,
        String currency,
        MembershipPromotionResponse promotion,
        BigDecimal discountAmount,
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
