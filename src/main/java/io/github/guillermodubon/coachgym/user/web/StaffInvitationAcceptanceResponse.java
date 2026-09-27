package io.github.guillermodubon.coachgym.user.web;

import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffInvitationAcceptanceResult;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record StaffInvitationAcceptanceResponse(
        UUID userId,
        RoleCode role,
        StaffScopeType scope,
        List<UUID> branchIds,
        Instant acceptedAt) {

    static StaffInvitationAcceptanceResponse from(StaffInvitationAcceptanceResult result) {
        return new StaffInvitationAcceptanceResponse(
                result.userId(), result.role(), result.scope(),
                result.branchIds().stream().sorted().toList(), result.acceptedAt());
    }
}
