package io.github.guillermodubon.coachgym.client.application;

import io.github.guillermodubon.coachgym.client.ClientPhotoDetails;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientPhotoApplicationService {

    private final ClientPhotoStore photoStore;
    private final ClientPhotoStorage photoStorage;
    private final Clock clock;

    public ClientPhotoApplicationService(
            ClientPhotoStore photoStore,
            ClientPhotoStorage photoStorage,
            Clock clock) {
        this.photoStore = Objects.requireNonNull(photoStore);
        this.photoStorage = Objects.requireNonNull(photoStorage);
        this.clock = Objects.requireNonNull(clock);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    @Transactional
    public ClientPhotoDetails upload(
            UUID clientId,
            UploadClientPhotoCommand command,
            AuthenticatedActor actor) {
        requireClientAndActor(clientId, actor);
        Objects.requireNonNull(command, "Client photo command is required.");
        if (!photoStore.clientExists(clientId)) {
            throw new ClientPhotoNotFoundException("Client was not found.");
        }

        String checksum = sha256(command.bytes());
        String extension = switch (command.contentType()) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new ClientPhotoValidationException("Unsupported client photo type.");
        };
        String newStorageKey = "clients/" + clientId + "/" + UUID.randomUUID() + extension;
        String previousStorageKey = photoStore.findByClientId(clientId)
                .map(ClientPhotoRecord::storageKey)
                .orElse(null);
        Instant occurredAt = clock.instant();

        photoStorage.store(newStorageKey, command.bytes());
        try {
            ClientPhotoDetails saved = photoStore.save(
                    clientId,
                    newStorageKey,
                    command.contentType(),
                    command.bytes().length,
                    checksum,
                    actor,
                    occurredAt);
            if (previousStorageKey != null && !previousStorageKey.equals(newStorageKey)) {
                photoStorage.delete(previousStorageKey);
            }
            return saved;
        } catch (RuntimeException exception) {
            photoStorage.delete(newStorageKey);
            throw exception;
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    @Transactional(readOnly = true)
    public ClientPhotoContent load(UUID clientId) {
        requireClientId(clientId);
        ClientPhotoRecord record = photoStore.findByClientId(clientId)
                .orElseThrow(() -> new ClientPhotoNotFoundException(
                        "Client photo was not found."));
        return photoStorage.load(
                record.storageKey(),
                record.details().contentType(),
                record.checksumSha256());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void delete(UUID clientId, long expectedVersion) {
        requireClientId(clientId);
        if (expectedVersion < 0) {
            throw new ClientPhotoValidationException(
                    "Client photo version must not be negative.");
        }
        String storageKey = photoStore.delete(clientId, expectedVersion);
        photoStorage.delete(storageKey);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static void requireClientAndActor(UUID clientId, AuthenticatedActor actor) {
        requireClientId(clientId);
        if (actor == null || actor.id() == null) {
            throw new ClientPhotoValidationException("Authenticated actor is required.");
        }
    }

    private static void requireClientId(UUID clientId) {
        if (clientId == null) {
            throw new ClientPhotoValidationException("Client id is required.");
        }
    }
}
