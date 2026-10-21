package io.github.guillermodubon.coachgym.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "LoginRequest")
public record LoginRequest(
        @NotBlank @Size(max = 254)
        @Schema(description = "Staff username or email address.", maxLength = 254,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String identifier,
        @NotBlank @Size(max = 256)
        @Schema(description = "Staff account password.", maxLength = 256, writeOnly = true,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String password) {
}
