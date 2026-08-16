package io.github.guillermodubon.coachgym.promotion;

import java.math.BigDecimal;
import java.util.UUID;

public record PromotionEvaluationResult(
        UUID promotionId,
        String promotionCode,
        String promotionName,
        DiscountType discountType,
        BigDecimal discountValue,
        String promotionCurrency,
        BigDecimal listPrice,
        String currency,
        BigDecimal discountAmount,
        BigDecimal finalPrice) {}

