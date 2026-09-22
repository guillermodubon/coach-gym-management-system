package io.github.guillermodubon.coachgym.user.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import io.github.guillermodubon.coachgym.user.AssignStaffToBranchCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Strict administrative request; role and status are never client controlled. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record StaffBranchAssignmentRequest(
        @NotNull UUID targetUserId,
        @NotNull UUID branchId,
        @NotNull @Size(max = 1_000) String reason) {

    AssignStaffToBranchCommand toCommand() {
        return new AssignStaffToBranchCommand(targetUserId, branchId, reason);
    }

    @JsonAnySetter
    void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported staff assignment request field: " + name);
    }
}
