package io.github.guillermodubon.coachgym.membership.web;

import io.github.guillermodubon.coachgym.membership.domain.MembershipPromotionSnapshot;
import io.github.guillermodubon.coachgym.promotion.DiscountType;
import java.math.BigDecimal;
import java.util.UUID;

public record MembershipPromotionResponse(
        UUID promotionId,
        String promotionCode,
        String promotionName,
        DiscountType discountType,
        BigDecimal discountValue,
        String currency) {

    static MembershipPromotionResponse from(
            MembershipPromotionSnapshot promotion) {

        if (promotion == null) {
            return null;
        }

        return new MembershipPromotionResponse(
                promotion.promotionId(),
                promotion.promotionCode(),
                promotion.promotionName(),
                promotion.discountType(),
                promotion.discountValue(),
                promotion.promotionCurrency());
    }
}
