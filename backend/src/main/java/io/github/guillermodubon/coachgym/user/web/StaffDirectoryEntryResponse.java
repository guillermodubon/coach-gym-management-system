package io.github.guillermodubon.coachgym.user.web;

import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import io.github.guillermodubon.coachgym.user.application.StaffDirectoryEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(name = "StaffDirectoryEntryResponse", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record StaffDirectoryEntryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID userId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String username,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String firstName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String lastName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        StaffAccountStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<RoleCode> roles,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        StaffScopeType scope,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long scopeVersion) {

    static StaffDirectoryEntryResponse from(StaffDirectoryEntry entry) {
        return new StaffDirectoryEntryResponse(
                entry.userId(), entry.username(), entry.firstName(), entry.lastName(),
                entry.status(), entry.roles().stream().sorted().toList(), entry.scope(),
                entry.version(), entry.scopeVersion());
    }
}
