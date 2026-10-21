package io.github.guillermodubon.coachgym.membership.web;

import io.github.guillermodubon.coachgym.membership.domain.MembershipPromotionSnapshot;
import io.github.guillermodubon.coachgym.promotion.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(name = "MembershipPromotionResponse", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record MembershipPromotionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID promotionId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String promotionCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String promotionName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        DiscountType discountType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal discountValue,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
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
