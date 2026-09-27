package io.github.guillermodubon.coachgym.user;

import java.util.Optional;
import java.util.UUID;

/**
 * Public user-module API consumed by authentication.
 */
public interface AuthenticationUserQuery {

    Optional<AuthenticatedUser> findActiveUserByIdentifier(String identifier);

    /** Reads authoritative state on each authenticated request for session freshness checks. */
    Optional<StaffAccountSecurityState> findAccountSecurityState(UUID userId);
}
