package io.github.guillermodubon.coachgym.promotion;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Public representation of a promotion exposed by the promotion module.
 *
 * <p>This record is independent of JPA and HTTP. Other modules may use it
 * without depending on promotion infrastructure.</p>
 */
public record PromotionDetails(
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
        long version) {}
