package io.github.guillermodubon.coachgym.user.web;

import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import io.github.guillermodubon.coachgym.user.application.StaffDirectoryDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "StaffDirectoryDetailsResponse", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record StaffDirectoryDetailsResponse(
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
        long scopeVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt) {

    static StaffDirectoryDetailsResponse from(StaffDirectoryDetails details) {
        return new StaffDirectoryDetailsResponse(
                details.userId(), details.username(), details.firstName(), details.lastName(),
                details.status(), details.roles().stream().sorted().toList(), details.scope(),
                details.version(), details.scopeVersion(), details.email(),
                details.createdAt(), details.updatedAt());
    }
}
