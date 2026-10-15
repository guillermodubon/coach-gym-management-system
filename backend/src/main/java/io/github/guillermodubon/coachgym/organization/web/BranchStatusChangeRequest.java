package io.github.guillermodubon.coachgym.organization.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.guillermodubon.coachgym.organization.ChangeGymBranchStatusCommand;
import io.github.guillermodubon.coachgym.organization.GymBranchStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Strict lifecycle request; the endpoint determines the requested status. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
        description = "Reason and current branch version for activation or deactivation. "
                + "The target status is selected by the endpoint.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record BranchStatusChangeRequest(
        @NotBlank @Size(max = 1000) String reason,
        @NotNull @PositiveOrZero Long version) {

    @JsonAnySetter
    void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported branch lifecycle field: " + name);
    }

    ChangeGymBranchStatusCommand toCommand(GymBranchStatus status) {
        return new ChangeGymBranchStatusCommand(status, reason, version);
    }
}
