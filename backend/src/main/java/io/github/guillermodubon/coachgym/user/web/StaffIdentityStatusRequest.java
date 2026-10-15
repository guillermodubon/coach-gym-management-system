package io.github.guillermodubon.coachgym.user.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.guillermodubon.coachgym.user.ChangeStaffIdentityStatusCommand;
import io.github.guillermodubon.coachgym.user.StaffIdentityStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record StaffIdentityStatusRequest(
        @PositiveOrZero long expectedVersion,
        @NotBlank @Size(max = 1_000) String reason,
        @Size(max = 256) String currentPassword) {

    ChangeStaffIdentityStatusCommand toCommand(UUID targetUserId, StaffIdentityStatus status) {
        return new ChangeStaffIdentityStatusCommand(targetUserId, status, reason, expectedVersion);
    }

    @Override
    public String toString() {
        return "StaffIdentityStatusRequest[expectedVersion=" + expectedVersion
                + ", reasonPresent=" + (reason != null)
                + ", currentPasswordPresent=" + (currentPassword != null) + ']';
    }

    @JsonAnySetter
    void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported staff status field: " + name);
    }
}
