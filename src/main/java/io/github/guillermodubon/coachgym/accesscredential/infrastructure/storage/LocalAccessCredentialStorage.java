package io.github.guillermodubon.coachgym.accesscredential.infrastructure.storage;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialDocument;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialStorage;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialStorageException;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialStorageKey;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialStoredDocument;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Local canonical PNG adapter with server-owned keys and atomic promotion. */
@Component
class LocalAccessCredentialStorage implements AccessCredentialStorage {

    private static final String CONTENT_TYPE = "image/png";
    private static final byte[] PNG_SIGNATURE = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private final Path root;

    LocalAccessCredentialStorage(AccessCredentialStorageProperties properties) {
        this.root = Objects.requireNonNull(properties)
                .getDirectory()
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public String generateStorageKey(UUID credentialId) {
        return AccessCredentialStorageKey.forCredential(credentialId);
    }

    @Override
    public AccessCredentialStoredDocument store(
            String storageKey,
            AccessCredentialDocument document,
            String rendererVersion) {
        if (document == null) {
            throw new AccessCredentialStorageException(
                    "Access credential document is required.");
        }
        byte[] bytes = document.bytes();
        requirePng(bytes);
        String canonicalKey = AccessCredentialStorageKey.requireCanonical(storageKey);
        String checksum = checksum(bytes);
        AccessCredentialStoredDocument metadata = new AccessCredentialStoredDocument(
                canonicalKey,
                CONTENT_TYPE,
                bytes.length,
                checksum,
                rendererVersion);
        Path target = resolve(canonicalKey);
        if (Files.exists(target)) {
            throw new AccessCredentialStorageException(
                    "Access credential artifact already exists.");
        }
        Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), ".credential-", ".tmp");
            Files.write(temporary, bytes,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target);
            }
            return metadata;
        } catch (FileAlreadyExistsException exception) {
            throw new AccessCredentialStorageException(
                    "Access credential artifact already exists.", exception);
        } catch (IOException exception) {
            throw new AccessCredentialStorageException(
                    "Access credential document could not be stored.", exception);
        } finally {
            deleteQuietly(temporary);
        }
    }

    @Override
    public AccessCredentialDocument load(
            String storageKey,
            String contentType,
            long sizeBytes,
            String checksumSha256) {
        String canonicalKey = AccessCredentialStorageKey.requireCanonical(storageKey);
        requireMetadata(contentType, sizeBytes, checksumSha256);
        Path source = resolve(canonicalKey);
        try {
            long reportedSize = Files.size(source);
            if (reportedSize != sizeBytes || reportedSize > AccessCredentialDocument.MAX_SIZE_BYTES) {
                throw new AccessCredentialStorageException(
                        "Stored access credential document size is invalid.");
            }
            byte[] bytes = readBounded(source);
            if (bytes.length != sizeBytes) {
                throw new AccessCredentialStorageException(
                        "Stored access credential document size is invalid.");
            }
            requirePng(bytes);
            if (!checksum(bytes).equals(checksumSha256)) {
                throw new AccessCredentialStorageException(
                        "Stored access credential document checksum does not match.");
            }
            return new AccessCredentialDocument(CONTENT_TYPE, bytes);
        } catch (NoSuchFileException exception) {
            throw new AccessCredentialStorageException(
                    "Access credential document could not be found.");
        } catch (AccessCredentialStorageException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new AccessCredentialStorageException(
                    "Access credential document could not be read.", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        String canonicalKey = AccessCredentialStorageKey.requireCanonical(storageKey);
        try {
            Files.deleteIfExists(resolve(canonicalKey));
        } catch (IOException exception) {
            throw new AccessCredentialStorageException(
                    "Access credential document could not be deleted.", exception);
        }
    }

    private Path resolve(String storageKey) {
        String canonicalKey = AccessCredentialStorageKey.requireCanonical(storageKey);
        Path resolved = root.resolve(canonicalKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new AccessCredentialStorageException("Invalid access credential storage key.");
        }
        Path current = root;
        for (Path component : root.relativize(resolved)) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new AccessCredentialStorageException(
                        "Invalid access credential storage key.");
            }
        }
        return resolved;
    }

    private static void requireMetadata(
            String contentType,
            long sizeBytes,
            String checksumSha256) {
        if (contentType == null
                || !CONTENT_TYPE.equals(contentType.strip().toLowerCase(Locale.ROOT))) {
            throw new AccessCredentialStorageException(
                    "Stored access credential content type is invalid.");
        }
        if (sizeBytes < 1 || sizeBytes > AccessCredentialDocument.MAX_SIZE_BYTES) {
            throw new AccessCredentialStorageException(
                    "Stored access credential document size is invalid.");
        }
        if (checksumSha256 == null || !checksumSha256.matches("[0-9a-f]{64}")) {
            throw new AccessCredentialStorageException(
                    "Stored access credential document checksum is invalid.");
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
                if (total > AccessCredentialDocument.MAX_SIZE_BYTES) {
                    throw new AccessCredentialStorageException(
                            "Stored access credential document size is invalid.");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static void requirePng(byte[] bytes) {
        if (bytes == null || bytes.length < PNG_SIGNATURE.length) {
            throw new AccessCredentialStorageException(
                    "Access credential document is not a PNG.");
        }
        for (int index = 0; index < PNG_SIGNATURE.length; index++) {
            if (bytes[index] != PNG_SIGNATURE[index]) {
                throw new AccessCredentialStorageException(
                        "Access credential document is not a PNG.");
            }
        }
    }

    private static String checksum(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Preserve the original storage failure and never log the local path.
        }
    }
}
