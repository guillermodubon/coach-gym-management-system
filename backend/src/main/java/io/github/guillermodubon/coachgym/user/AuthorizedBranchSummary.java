package io.github.guillermodubon.coachgym.user;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import java.util.UUID;

/** Minimal safe branch projection for a staff member's branch context. */
@Schema(name = "AuthorizedBranchSummary", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record AuthorizedBranchSummary(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID organizationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String timezone,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean initialBranch) {

    public AuthorizedBranchSummary {
        id = StaffAssignmentValuePolicy.requireId(id, "id");
        organizationId = StaffAssignmentValuePolicy.requireId(organizationId, "organizationId");
        code = StaffAssignmentValuePolicy.requireCode(code, "code");
        name = StaffAssignmentValuePolicy.requireText(name, "name", 160);
        timezone = StaffAssignmentValuePolicy.requireTimezone(timezone);
    }
}
