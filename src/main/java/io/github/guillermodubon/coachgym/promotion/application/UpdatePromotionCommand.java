package io.github.guillermodubon.coachgym.promotion.application;

import io.github.guillermodubon.coachgym.promotion.DiscountType;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Applic*tion input required to update a pr*motion.
 */
public record UpdatePromotionCommand(
        String name,
        String description,
        DiscountType discountType,
        BigDecimal discountValue,
        String currency,
        LocalDate validFrom,
        LocalDate validUntil,
        long version) {}
