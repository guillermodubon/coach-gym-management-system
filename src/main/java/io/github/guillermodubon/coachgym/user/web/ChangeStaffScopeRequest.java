package io.github.guillermodubon.coachgym.user.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import io.github.guillermodubon.coachgym.user.ChangeStaffScopeCommand;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Strict scope request; roles and permissions remain server controlled. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record ChangeStaffScopeRequest(
        @NotNull StaffScopeType requestedScope,
        @NotBlank @Size(max = 1_000) String reason,
        @Min(0) long expectedVersion) {

    ChangeStaffScopeCommand toCommand(UUID targetUserId) {
        return new ChangeStaffScopeCommand(targetUserId, requestedScope, reason, expectedVersion);
    }

    @JsonAnySetter
    void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported staff scope request field: " + name);
    }
}
