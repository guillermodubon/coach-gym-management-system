package io.github.guillermodubon.coachgym.client;

import java.util.Optional;
import java.util.UUID;

/**
 * Public client-module boundary used by other business modules.
 *
 * <p>The caller remains responsible for deciding whether the returned
 * client status is valid for its use case.</p>
 */
public interface ClientQuery {

    Optional<ClientDetails> findClientById(
            UUID clientId);
}
