package io.github.guillermodubon.coachgym.promotion.web;

import io.github.guillermodubon.coachgym.promotion.DiscountType;
import io.github.guillermodubon.coachgym.promotion.PromotionDetails;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PromotionResponse(
        UUID id,
        String promotionCode,
        String name,
        String description,
        DiscountType discountType,
        BigDecimal discountValue,
        String currency,
        LocalDate validFrom,
        LocalDate validUntil,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    static PromotionResponse from(PromotionDetails promotion) {
        return new PromotionResponse(
                promotion.id(),
                promotion.promotionCode(),
                promotion.name(),
                promotion.description(),
                promotion.discountType(),
                promotion.discountValue(),
                promotion.currency(),
                promotion.validFrom(),
                promotion.validUntil(),
                promotion.active(),
                promotion.createdAt(),
                promotion.updatedAt(),
                promotion.version());
    }
}
