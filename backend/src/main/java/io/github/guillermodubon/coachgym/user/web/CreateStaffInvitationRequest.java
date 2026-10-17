package io.github.guillermodubon.coachgym.user.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.guillermodubon.coachgym.user.CreateStaffInvitationCommand;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

/** Strict organization-admin invite command; current password is redacted from diagnostics. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record CreateStaffInvitationRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull RoleCode proposedRole,
        @NotNull StaffScopeType proposedScope,
        @NotNull @Size(max = 100) Set<@NotNull UUID> branchIds,
        @Size(max = 256) String currentPassword) {

    CreateStaffInvitationCommand toCommand() {
        return new CreateStaffInvitationCommand(email, proposedRole, proposedScope, branchIds);
    }

    @Override
    public String toString() {
        return "CreateStaffInvitationRequest[emailPresent=" + (email != null)
                + ", proposedRole=" + proposedRole
                + ", proposedScope=" + proposedScope
                + ", branchCount=" + (branchIds == null ? 0 : branchIds.size())
                + ", currentPasswordPresent=" + (currentPassword != null) + ']';
    }

    @JsonAnySetter
    void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported staff invitation request field: " + name);
    }
}
