package io.github.guillermodubon.coachgym.access.web;

import io.github.guillermodubon.coachgym.access.application.CheckInCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request used to process a gym access check-in.
 *
 * <p>The web layer validates only the structural constraints. Identifier
 * normalization and interpretation remain responsibilities of the access
 * domain.</p>
 */
public record CheckInRequest(
        @NotBlank @Size(max = 64) String identifier
) {
    public CheckInCommand toCommand() {
        return new CheckInCommand(identifier);
    }
}
