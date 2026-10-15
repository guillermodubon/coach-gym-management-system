package io.github.guillermodubon.coachgym.configuration;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "gym")
public record GymProperties(@NotBlank String timeZone, @NotBlank String currency) {
}
