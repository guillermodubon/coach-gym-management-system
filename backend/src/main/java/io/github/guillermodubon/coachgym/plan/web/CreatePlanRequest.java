package io.github.guillermodubon.coachgym.plan.web;

import io.github.guillermodubon.coachgym.plan.DurationUnit;
import io.github.guillermodubon.coachgym.plan.application.CreatePlanCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreatePlanRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 2_000) String description,
        @NotNull @Positive @Max(Short.MAX_VALUE) Integer durationValue,
        @NotNull DurationUnit durationUnit,
        @NotNull @DecimalMin(value = "0.00") @Digits(integer = 10, fraction = 2) BigDecimal listPrice,
        @NotBlank @Pattern(regexp = "(?i)[A-Z]{3}") String currency) {

    CreatePlanCommand toCommand() {
        return new CreatePlanCommand(
                name,
                description,
                durationValue,
                durationUnit,
                listPrice,
                currency);
    }
}
