package io.github.guillermodubon.coachgym.user.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.guillermodubon.coachgym.user.ChangeStaffRoleScopeCommand;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record ChangeStaffRoleScopeRequest(
        @NotEmpty @Size(max = 2) Set<@NotNull RoleCode> roles,
        @NotNull StaffScopeType scope,
        @NotNull @Size(max = 1_000) String reason,
        @PositiveOrZero long expectedVersion,
        @NotNull @Size(max = 256) String currentPassword) {

    ChangeStaffRoleScopeCommand toCommand(UUID targetUserId) {
        return new ChangeStaffRoleScopeCommand(
                targetUserId, roles, scope, reason, expectedVersion);
    }

    @Override
    public String toString() {
        return "ChangeStaffRoleScopeRequest[roles=" + roles
                + ", scope=" + scope
                + ", reasonPresent=" + (reason != null)
                + ", expectedVersion=" + expectedVersion
                + ", currentPasswordPresent=" + (currentPassword != null) + ']';
    }

    @JsonAnySetter
    void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported staff role/scope field: " + name);
    }
}
