package io.github.guillermodubon.coachgym.user.infrastructure.storage;

import io.github.guillermodubon.coachgym.user.application.StaffProfilePhotoContent;
import io.github.guillermodubon.coachgym.user.application.StaffProfilePhotoInspector;
import io.github.guillermodubon.coachgym.user.application.StaffProfilePhotoStorage;
import io.github.guillermodubon.coachgym.user.application.StaffProfilePhotoStorageException;
import io.github.guillermodubon.coachgym.user.application.StaffProfilePhotoStorageKey;
import io.github.guillermodubon.coachgym.user.application.StaffProfileValidationException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Local private-object adapter with atomic promotion and path containment. */
@Component
@ConditionalOnProperty(
        prefix = "gym.storage", name = "provider", havingValue = "local", matchIfMissing = true)
class LocalStaffProfilePhotoStorage implements StaffProfilePhotoStorage {

    private final Path root;

    LocalStaffProfilePhotoStorage(StaffProfilePhotoStorageProperties properties) {
        this.root = properties.getDirectory().toAbsolutePath().normalize();
    }

    @Override
    public String generateStorageKey(UUID userId, String contentType) {
        return StaffProfilePhotoStorageKey.forUser(userId, contentType);
    }

    @Override
    public void store(String storageKey, StaffProfilePhotoContent content) {
        StaffProfilePhotoContent safeContent = requireSafeContent(storageKey, content);
        Path target = resolve(storageKey);
        if (Files.exists(target)) {
            throw new StaffProfilePhotoStorageException(
                    "Staff profile photo artifact already exists.");
        }
        Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(
                    target.getParent(), ".staff-photo-", ".tmp");
            Files.write(temporary, safeContent.bytes(),
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target);
            }
        } catch (FileAlreadyExistsException exception) {
            throw new StaffProfilePhotoStorageException(
                    "Staff profile photo artifact already exists.", exception);
        } catch (IOException exception) {
            throw new StaffProfilePhotoStorageException(
                    "Staff profile photo could not be stored.", exception);
        } finally {
            deleteQuietly(temporary);
        }
    }

    @Override
    public StaffProfilePhotoContent load(
            String storageKey,
            String contentType,
            long expectedSizeBytes,
            String expectedChecksumSha256) {
        String canonicalKey = StaffProfilePhotoStorageKey
                .requireCanonicalForContentType(storageKey, contentType);
        requireMetadata(contentType, expectedSizeBytes, expectedChecksumSha256);
        Path source = resolve(canonicalKey);
        try {
            long reportedSize = Files.size(source);
            if (reportedSize != expectedSizeBytes) {
                throw new StaffProfilePhotoStorageException(
                        "Stored staff profile photo size is invalid.");
            }
            byte[] bytes = readBounded(source);
            if (bytes.length != expectedSizeBytes) {
                throw new StaffProfilePhotoStorageException(
                        "Stored staff profile photo size is invalid.");
            }
            StaffProfilePhotoContent content = new StaffProfilePhotoContent(
                    contentType, bytes, expectedChecksumSha256);
            StaffProfilePhotoInspector.requireSafe(content);
            return content;
        } catch (NoSuchFileException exception) {
            throw new StaffProfilePhotoStorageException(
                    "Staff profile photo could not be found.");
        } catch (StaffProfilePhotoStorageException exception) {
            throw exception;
        } catch (StaffProfileValidationException exception) {
            throw new StaffProfilePhotoStorageException(
                    "Stored staff profile photo is invalid.", exception);
        } catch (IOException exception) {
            throw new StaffProfilePhotoStorageException(
                    "Staff profile photo could not be read.", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        String canonicalKey = StaffProfilePhotoStorageKey.requireCanonical(storageKey);
        try {
            Files.deleteIfExists(resolve(canonicalKey));
        } catch (IOException exception) {
            throw new StaffProfilePhotoStorageException(
                    "Staff profile photo could not be deleted.", exception);
        }
    }

    private StaffProfilePhotoContent requireSafeContent(
            String storageKey, StaffProfilePhotoContent content) {
        StaffProfilePhotoStorageKey.requireCanonicalForContentType(
                storageKey, content == null ? null : content.contentType());
        try {
            StaffProfilePhotoInspector.requireSafe(content);
            return content;
        } catch (StaffProfileValidationException exception) {
            throw new StaffProfilePhotoStorageException(
                    "Staff profile photo is invalid.", exception);
        }
    }

    private Path resolve(String storageKey) {
        String canonicalKey = StaffProfilePhotoStorageKey.requireCanonical(storageKey);
        Path resolved = root.resolve(canonicalKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new StaffProfilePhotoStorageException(
                    "Invalid staff profile photo storage key.");
        }
        Path current = root;
        for (Path component : root.relativize(resolved)) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new StaffProfilePhotoStorageException(
                        "Invalid staff profile photo storage key.");
            }
        }
        return resolved;
    }

    private static void requireMetadata(
            String contentType, long expectedSizeBytes, String checksumSha256) {
        StaffProfilePhotoStorageKey.extensionFor(contentType);
        if (expectedSizeBytes < 1
                || expectedSizeBytes > StaffProfilePhotoContent.MAX_SIZE_BYTES) {
            throw new StaffProfilePhotoStorageException(
                    "Stored staff profile photo size is invalid.");
        }
        if (checksumSha256 == null || !checksumSha256.matches("[0-9a-f]{64}")) {
            throw new StaffProfilePhotoStorageException(
                    "Stored staff profile photo checksum is invalid.");
        }
    }

    private static byte[] readBounded(Path source) throws IOException {
        try (InputStream input = Files.newInputStream(source);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            long total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > StaffProfilePhotoContent.MAX_SIZE_BYTES) {
                    throw new StaffProfilePhotoStorageException(
                            "Stored staff profile photo size is invalid.");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Preserve the original failure without exposing a local path.
        }
    }
}
