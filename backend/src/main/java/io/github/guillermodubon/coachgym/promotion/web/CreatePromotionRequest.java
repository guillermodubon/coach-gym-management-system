package io.github.guillermodubon.coachgym.promotion.web;

import io.github.guillermodubon.coachgym.promotion.DiscountType;
import io.github.guillermodubon.coachgym.promotion.application.CreatePromotionCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreatePromotionRequest(
        @NotBlank
        @Size(max = 160)
        String name,

        @Size(max = 2_000)
        String description,

        @NotNull
        DiscountType discountType,

        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 10, fraction = 2)
        BigDecimal discountValue,

        @Pattern(regexp = "(?i)[A-Z]{3}")
        String currency,

        @NotNull
        LocalDate validFrom,

        @NotNull
        LocalDate validUntil) {

    CreatePromotionCommand toCommand() {
        return new CreatePromotionCommand(
                name,
                description,
                discountType,
                discountValue,
                currency,
                validFrom,
                validUntil);
    }
}
