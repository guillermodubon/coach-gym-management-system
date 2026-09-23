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

    /**
     * Resolves a client only when its immutable home branch matches the
     * authoritative branch context of the caller.
     */
    default Optional<ClientDetails> findClientById(
            UUID clientId,
            UUID homeBranchId) {
        return findClientById(clientId);
    }
}
