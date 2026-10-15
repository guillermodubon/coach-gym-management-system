package io.github.guillermodubon.coachgym.user.infrastructure.storage;

import io.github.guillermodubon.coachgym.shared.storage.SupabaseStorageClient;
import io.github.guillermodubon.coachgym.user.application.StaffProfilePhotoContent;
import io.github.guillermodubon.coachgym.user.application.StaffProfilePhotoInspector;
import io.github.guillermodubon.coachgym.user.application.StaffProfilePhotoStorage;
import io.github.guillermodubon.coachgym.user.application.StaffProfilePhotoStorageException;
import io.github.guillermodubon.coachgym.user.application.StaffProfilePhotoStorageKey;
import io.github.guillermodubon.coachgym.user.application.StaffProfileValidationException;
import java.util.UUID;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Private Supabase Storage adapter for staff profile photos. */
@Component
@ConditionalOnProperty(prefix = "gym.storage", name = "provider", havingValue = "supabase")
class SupabaseStaffProfilePhotoStorage implements StaffProfilePhotoStorage {

    private final SupabaseStorageClient client;

    SupabaseStaffProfilePhotoStorage(SupabaseStorageClient client) {
        this.client = Objects.requireNonNull(client);
    }

    @Override
    public String generateStorageKey(UUID userId, String contentType) {
        return StaffProfilePhotoStorageKey.forUser(userId, contentType);
    }

    @Override
    public void store(String storageKey, StaffProfilePhotoContent content) {
        StaffProfilePhotoContent safeContent = requireSafeContent(storageKey, content);
        try {
            client.put(
                    StaffProfilePhotoStorageKey.requireCanonicalForContentType(
                            storageKey, safeContent.contentType()),
                    safeContent.contentType(),
                    safeContent.bytes());
        } catch (SupabaseStorageClient.StorageProviderException exception) {
            throw new StaffProfilePhotoStorageException(
                    "Staff profile photo could not be stored.", exception);
        }
    }

    @Override
    public StaffProfilePhotoContent load(
            String storageKey,
            String contentType,
            long expectedSizeBytes,
            String expectedChecksumSha256) {
        requireMetadata(contentType, expectedSizeBytes, expectedChecksumSha256);
        String canonicalKey = StaffProfilePhotoStorageKey
                .requireCanonicalForContentType(storageKey, contentType);
        try {
            byte[] bytes = client.get(
                    canonicalKey,
                    contentType.strip().toLowerCase(java.util.Locale.ROOT),
                    expectedSizeBytes,
                    expectedChecksumSha256);
            StaffProfilePhotoContent content = new StaffProfilePhotoContent(
                    contentType, bytes, expectedChecksumSha256);
            StaffProfilePhotoInspector.requireSafe(content);
            return content;
        } catch (SupabaseStorageClient.StorageProviderException exception) {
            throw new StaffProfilePhotoStorageException(
                    "Staff profile photo could not be read.", exception);
        } catch (StaffProfileValidationException exception) {
            throw new StaffProfilePhotoStorageException(
                    "Stored staff profile photo is invalid.", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            client.delete(StaffProfilePhotoStorageKey.requireCanonical(storageKey));
        } catch (SupabaseStorageClient.StorageProviderException exception) {
            throw new StaffProfilePhotoStorageException(
                    "Staff profile photo could not be deleted.", exception);
        }
    }

    private static StaffProfilePhotoContent requireSafeContent(
            String storageKey, StaffProfilePhotoContent content) {
        String contentType = content == null ? null : content.contentType();
        StaffProfilePhotoStorageKey.requireCanonicalForContentType(storageKey, contentType);
        try {
            StaffProfilePhotoInspector.requireSafe(content);
            return content;
        } catch (StaffProfileValidationException exception) {
            throw new StaffProfilePhotoStorageException(
                    "Staff profile photo is invalid.", exception);
        }
    }

    private static void requireMetadata(
            String contentType, long expectedSizeBytes, String expectedChecksumSha256) {
        StaffProfilePhotoStorageKey.extensionFor(contentType);
        if (expectedSizeBytes < 1
                || expectedSizeBytes > StaffProfilePhotoContent.MAX_SIZE_BYTES) {
            throw new StaffProfilePhotoStorageException(
                    "Stored staff profile photo size is invalid.");
        }
        if (expectedChecksumSha256 == null
                || !expectedChecksumSha256.matches("[0-9a-f]{64}")) {
            throw new StaffProfilePhotoStorageException(
                    "Stored staff profile photo checksum is invalid.");
        }
    }
}
