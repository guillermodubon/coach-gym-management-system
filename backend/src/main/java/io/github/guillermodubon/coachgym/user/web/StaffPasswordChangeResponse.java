package io.github.guillermodubon.coachgym.user.web;

import io.github.guillermodubon.coachgym.user.application.StaffPasswordChangeResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Safe password-change result; it contains no credential material. */
@Schema(name = "StaffPasswordChangeResponse", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record StaffPasswordChangeResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID userId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long profileVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean reauthenticationRequired) {

    static StaffPasswordChangeResponse from(StaffPasswordChangeResult result) {
        return new StaffPasswordChangeResponse(
                result.userId(),
                result.profileVersion(),
                result.reauthenticationRequired());
    }
}
