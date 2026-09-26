package io.github.guillermodubon.coachgym.client.application;

import io.github.guillermodubon.coachgym.client.ClientPhotoDetails;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ClientPhotoStore {

    boolean clientExists(UUID clientId);

    default boolean clientExists(UUID clientId, UUID branchId) {
        return clientExists(clientId);
    }

    Optional<ClientPhotoRecord> findByClientId(UUID clientId);

    default Optional<ClientPhotoRecord> findByClientId(UUID clientId, UUID branchId) {
        return findByClientId(clientId);
    }

    ClientPhotoDetails save(
            UUID clientId,
            String storageKey,
            String contentType,
            long sizeBytes,
            String checksumSha256,
            AuthenticatedActor actor,
            Instant occurredAt,
            UUID branchId);

    default ClientPhotoDetails save(
            UUID clientId,
            String storageKey,
            String contentType,
            long sizeBytes,
            String checksumSha256,
            AuthenticatedActor actor,
            Instant occurredAt) {
        return save(clientId, storageKey, contentType, sizeBytes, checksumSha256,
                actor, occurredAt, null);
    }

    String delete(UUID clientId, long expectedVersion);

    default String delete(UUID clientId, long expectedVersion, UUID branchId) {
        return delete(clientId, expectedVersion);
    }
}
