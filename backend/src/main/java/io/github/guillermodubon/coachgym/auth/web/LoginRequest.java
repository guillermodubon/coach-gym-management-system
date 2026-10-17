package io.github.guillermodubon.coachgym.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 254) String identifier,
        @NotBlank @Size(max = 256) String password) {
}
