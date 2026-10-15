package io.github.guillermodubon.coachgym.user.web;

import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountStatus;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import io.github.guillermodubon.coachgym.user.application.StaffIdentityAdministrationState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record StaffIdentityAdministrationResponse(
        UUID userId,
        StaffAccountStatus status,
        List<RoleCode> roles,
        StaffScopeType scope,
        long version,
        long scopeVersion) {

    static StaffIdentityAdministrationResponse from(StaffIdentityAdministrationState state) {
        return new StaffIdentityAdministrationResponse(
                state.userId(), state.accountStatus(),
                state.roles().stream().sorted().toList(), state.scopeType(),
                state.securityVersion(), state.scopeVersion());
    }
}
