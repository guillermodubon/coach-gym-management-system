package io.github.guillermodubon.coachgym.user;

import java.util.UUID;

/**
 * Minimal identity snapshot used by business use cases that need to record an actor.
 *
 * <p>It intentionally excludes credentials and authorities. Authorization remains the
 * responsibility of Spring Security at the application boundary.</p>
 */
public record AuthenticatedActor(UUID id, String username) {
}
