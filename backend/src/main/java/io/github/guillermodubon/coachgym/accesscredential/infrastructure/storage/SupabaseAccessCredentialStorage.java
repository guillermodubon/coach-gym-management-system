package io.github.guillermodubon.coachgym.accesscredential.infrastructure.storage;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialDocument;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialStorage;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialStorageException;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialStorageKey;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialStoredDocument;
import io.github.guillermodubon.coachgym.shared.storage.SupabaseStorageClient;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Shared-object-storage QR credential adapter used by the Supabase profile. */
@Component
@ConditionalOnProperty(prefix = "gym.storage", name = "provider", havingValue = "supabase")
class SupabaseAccessCredentialStorage implements AccessCredentialStorage {

    private static final String CONTENT_TYPE = "image/png";
    private static final byte[] PNG_SIGNATURE = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private final SupabaseStorageClient client;

    SupabaseAccessCredentialStorage(SupabaseStorageClient client) {
        this.client = Objects.requireNonNull(client);
    }

    @Override
    public String generateStorageKey(UUID credentialId) {
        return AccessCredentialStorageKey.forCredential(credentialId);
    }

    @Override
    public AccessCredentialStoredDocument store(
            String storageKey, AccessCredentialDocument document, String rendererVersion) {
        if (document == null) {
            throw new AccessCredentialStorageException("Access credential document is required.");
        }
        requirePng(document.bytes());
        String key = AccessCredentialStorageKey.requireCanonical(storageKey);
        String checksum = checksum(document.bytes());
        try {
            client.put(key, CONTENT_TYPE, document.bytes());
        } catch (SupabaseStorageClient.StorageProviderException exception) {
            throw new AccessCredentialStorageException(
                    "Access credential document could not be stored.", exception);
        }
        return new AccessCredentialStoredDocument(
                key, CONTENT_TYPE, document.bytes().length, checksum, rendererVersion);
    }

    @Override
    public AccessCredentialDocument load(
            String storageKey, String contentType, long sizeBytes, String checksumSha256) {
        if (!CONTENT_TYPE.equals(contentType) || sizeBytes < 1
                || sizeBytes > AccessCredentialDocument.MAX_SIZE_BYTES
                || checksumSha256 == null || !checksumSha256.matches("[0-9a-f]{64}")) {
            throw new AccessCredentialStorageException("Stored access credential metadata is invalid.");
        }
        try {
            byte[] bytes = client.get(
                    AccessCredentialStorageKey.requireCanonical(storageKey),
                    CONTENT_TYPE, sizeBytes, checksumSha256);
            requirePng(bytes);
            return new AccessCredentialDocument(CONTENT_TYPE, bytes);
        } catch (SupabaseStorageClient.StorageProviderException exception) {
            throw new AccessCredentialStorageException(
                    "Access credential document could not be read.", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            client.delete(AccessCredentialStorageKey.requireCanonical(storageKey));
        } catch (SupabaseStorageClient.StorageProviderException exception) {
            throw new AccessCredentialStorageException(
                    "Access credential document could not be deleted.", exception);
        }
    }

    private static void requirePng(byte[] bytes) {
        if (bytes == null || bytes.length < PNG_SIGNATURE.length) {
            throw new AccessCredentialStorageException("Access credential document is not a PNG.");
        }
        for (int index = 0; index < PNG_SIGNATURE.length; index++) {
            if (bytes[index] != PNG_SIGNATURE[index]) {
                throw new AccessCredentialStorageException("Access credential document is not a PNG.");
            }
        }
    }

    private static String checksum(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
