package io.github.guillermodubon.coachgym.client.infrastructure.storage;

import io.github.guillermodubon.coachgym.client.application.ClientPhotoContent;
import io.github.guillermodubon.coachgym.client.application.ClientPhotoStorage;
import io.github.guillermodubon.coachgym.client.application.ClientPhotoStorageException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
class LocalClientPhotoStorage implements ClientPhotoStorage {

    private final Path root;

    LocalClientPhotoStorage(ClientPhotoStorageProperties properties) {
        this.root = properties.getDirectory().toAbsolutePath().normalize();
    }

    @Override
    public void store(String storageKey, byte[] content) {
        Path target = resolve(storageKey);
        Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            Files.write(temporary, content);
            Files.move(temporary, target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            deleteQuietly(temporary);
            throw new ClientPhotoStorageException("Client photo could not be stored.", exception);
        }
    }

    @Override
    public ClientPhotoContent load(
            String storageKey,
            String contentType,
            String checksumSha256) {
        try {
            byte[] bytes = Files.readAllBytes(resolve(storageKey));
            String actualChecksum = checksum(bytes);
            if (!actualChecksum.equals(checksumSha256)) {
                throw new ClientPhotoStorageException("Stored client photo checksum does not match.");
            }
            return new ClientPhotoContent(contentType, bytes, actualChecksum);
        } catch (IOException exception) {
            throw new ClientPhotoStorageException("Client photo could not be read.", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException exception) {
            throw new ClientPhotoStorageException("Client photo could not be deleted.", exception);
        }
    }

    private Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new ClientPhotoStorageException("Client photo storage key is required.");
        }
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new ClientPhotoStorageException("Invalid client photo storage key.");
        }
        return resolved;
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
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // Preserve the original storage failure.
            }
        }
    }
}
