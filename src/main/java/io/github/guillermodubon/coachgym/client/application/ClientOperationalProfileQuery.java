package io.github.guillermodubon.coachgym.client.application;

import io.github.guillermodubon.coachgym.client.ClientOperationalProfile;
import java.util.Optional;
import java.util.UUID;

/** Read port for the consolidated current client profile. */
public interface ClientOperationalProfileQuery {
    Optional<ClientOperationalProfile> findById(UUID clientId);

    default Optional<ClientOperationalProfile> findById(UUID clientId, UUID branchId) {
        return findById(clientId);
    }
}
