package io.github.guillermodubon.coachgym.user;

/**
 * Public authentication boundary used by user-owned web adapters to derive
 * the authenticated staff actor without depending on the auth module's
 * principal implementation.
 */
public interface AuthenticatedActorProvider {

    AuthenticatedActor authenticatedActor();
}
