package io.github.guillermodubon.coachgym.user;

import java.util.Optional;

/**
 * Public user-module API consumed by authentication.
 */
public interface AuthenticationUserQuery {

    Optional<AuthenticatedUser> findActiveUserByIdentifier(String identifier);
}
