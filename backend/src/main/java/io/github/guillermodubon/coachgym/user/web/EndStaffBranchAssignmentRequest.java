package io.github.guillermodubon.coachgym.user.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import io.github.guillermodubon.coachgym.user.EndStaffBranchAssignmentCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Strict lifecycle request with an optimistic version and bounded reason. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record EndStaffBranchAssignmentRequest(
        @NotBlank @Size(max = 1_000) String reason,
        @Min(0) long expectedVersion) {

    EndStaffBranchAssignmentCommand toCommand(UUID assignmentId) {
        return new EndStaffBranchAssignmentCommand(assignmentId, reason, expectedVersion);
    }

    @JsonAnySetter
    void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported staff assignment end field: " + name);
    }
}
