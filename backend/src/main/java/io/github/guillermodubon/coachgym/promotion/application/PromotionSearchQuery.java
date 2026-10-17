package io.github.guillermodubon.coachgym.promotion.application;

import io.github.guillermodubon.coachgym.promotion.DiscountType;
import io.github.guillermodubon.coachgym.promotion.domain.PromotionValidationException;
import java.time.LocalDate;
import java.util.Locale;

public record PromotionSearchQuery(
        Boolean active,
        String name,
        DiscountType discountType,
        LocalDate validOn,
        int page,
        int size,
        PromotionSortField sortField,
        PromotionSortDirection direction) {

    public static final int DEFAULT_SIZE = 25;
    public static final int MAX_SIZE = 100;

    public PromotionSearchQuery {
        if (page < 0) {
            throw new PromotionValidationException(
                    "Page must not be negative.");
        }

        if (size < 1 || size > MAX_SIZE) {
            throw new PromotionValidationException(
                    "Page size must be between 1 and 100.");
        }

        if (sortField == null) {
            throw new PromotionValidationException(
                    "Promotion sort field must be provided.");
        }

        if (direction == null) {
            throw new PromotionValidationException(
                    "Promotion sort direction must be provided.");
        }

        name = name == null || name.isBlank()
                ? ""
                : name.trim();
    }

    public static PromotionSearchQuery from(
            Boolean active,
            String name,
            DiscountType discountType,
            LocalDate validOn,
            int page,
            int size,
            String sort,
            String direction) {

        return new PromotionSearchQuery(
                active,
                name,
                discountType,
                validOn,
                page,
                size,
                parseSortField(sort),
                parseDirection(direction));
    }

    private static PromotionSortField parseSortField(
            String value) {

        String normalized =
                value == null || value.isBlank()
                        ? "NAME"
                        : value.trim()
                          .toUpperCase(Locale.ROOT);

        try {
            return PromotionSortField.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new PromotionValidationException(
                    "Unsupported promotion sort field.");
        }
    }

    private static PromotionSortDirection parseDirection(
            String value) {

        String normalized =
                value == null || value.isBlank()
                        ? "ASC"
                        : value.trim()
                          .toUpperCase(Locale.ROOT);

        try {
            return PromotionSortDirection.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new PromotionValidationException(
                    "Unsupported promotion sort direction.");
        }
    }
}
