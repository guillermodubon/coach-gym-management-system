package io.github.guillermodubon.coachgym.client.infrastructure.storage;

import io.github.guillermodubon.coachgym.client.application.ClientPhotoContent;
import io.github.guillermodubon.coachgym.client.application.ClientPhotoStorage;
import io.github.guillermodubon.coachgym.client.application.ClientPhotoStorageException;
import io.github.guillermodubon.coachgym.shared.storage.SupabaseStorageClient;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Shared-object-storage client-photo adapter used by the Supabase profile. */
@Component
@ConditionalOnProperty(prefix = "gym.storage", name = "provider", havingValue = "supabase")
class SupabaseClientPhotoStorage implements ClientPhotoStorage {

    private final SupabaseStorageClient client;

    SupabaseClientPhotoStorage(SupabaseStorageClient client) {
        this.client = Objects.requireNonNull(client);
    }

    @Override
    public void store(String storageKey, byte[] content) {
        try {
            client.put(requireKey(storageKey), contentType(storageKey), content);
        } catch (SupabaseStorageClient.StorageProviderException exception) {
            throw new ClientPhotoStorageException(
                    "Client photo could not be stored.", exception);
        }
    }

    @Override
    public ClientPhotoContent load(
            String storageKey, String contentType, String checksumSha256) {
        if (!contentType.equals("image/jpeg") && !contentType.equals("image/png")
                && !contentType.equals("image/webp")) {
            throw new ClientPhotoStorageException("Stored client photo content type is invalid.");
        }
        try {
            byte[] bytes = client.get(requireKey(storageKey), contentType, -1, checksumSha256);
            return new ClientPhotoContent(contentType, bytes, checksumSha256);
        } catch (SupabaseStorageClient.StorageProviderException exception) {
            throw new ClientPhotoStorageException(
                    "Client photo could not be read.", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            client.delete(requireKey(storageKey));
        } catch (SupabaseStorageClient.StorageProviderException exception) {
            throw new ClientPhotoStorageException(
                    "Client photo could not be deleted.", exception);
        }
    }

    private static String requireKey(String key) {
        if (key == null || key.isBlank() || key.contains("..")
                || key.startsWith("/") || key.endsWith("/")) {
            throw new ClientPhotoStorageException("Invalid client photo storage key.");
        }
        return key.strip();
    }

    private static String contentType(String key) {
        String lower = key.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        throw new ClientPhotoStorageException("Unsupported client photo type.");
    }
}
