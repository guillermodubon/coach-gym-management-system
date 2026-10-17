package io.github.guillermodubon.coachgym.user.web;

import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffScopeDetails;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Safe scope projection; grant actor internals are not exposed. */
record StaffScopeResponse(
        UUID userId,
        Set<RoleCode> roles,
        StaffScopeType scopeType,
        Instant grantedAt,
        long version) {

    static StaffScopeResponse from(StaffScopeDetails details) {
        return new StaffScopeResponse(
                details.userId(), details.roles(), details.scopeType(),
                details.grantedAt(), details.version());
    }
}
