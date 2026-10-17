package io.github.guillermodubon.coachgym.promotion.application;

import io.github.guillermodubon.coachgym.promotion.DiscountType;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Application input required to create a promotion.
 */
public record CreatePromotionCommand(
        String name,
        String description,
        DiscountType discountType,
        BigDecimal discountValue,
        String currency,
        LocalDate validFrom,
        LocalDate validUntil) {}

