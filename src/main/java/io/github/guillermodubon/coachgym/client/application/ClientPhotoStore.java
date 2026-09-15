package io.github.guillermodubon.coachgym.client.application;

import io.github.guillermodubon.coachgym.client.ClientPhotoDetails;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ClientPhotoStore {

    boolean clientExists(UUID clientId);

    Optional<ClientPhotoRecord> findByClientId(UUID clientId);

    ClientPhotoDetails save(
            UUID clientId,
            String storageKey,
            String contentType,
            long sizeBytes,
            String checksumSha256,
            AuthenticatedActor actor,
            Instant occurredAt);

    String delete(UUID clientId, long expectedVersion);
}
