package io.github.guillermodubon.coachgym.client.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.client.application.ClientPhotoStorageException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalClientPhotoStorageTest {

    @TempDir
    Path directory;

    @Test
    void storesLoadsAndDeletesWithinConfiguredRoot() throws Exception {
        ClientPhotoStorageProperties properties = new ClientPhotoStorageProperties();
        properties.setDirectory(directory);
        LocalClientPhotoStorage storage = new LocalClientPhotoStorage(properties);
        byte[] bytes = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1};
        String checksum = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));

        storage.store("clients/a/photo.jpg", bytes);
        assertThat(storage.load("clients/a/photo.jpg", "image/jpeg", checksum).bytes())
                .containsExactly(bytes);
        storage.delete("clients/a/photo.jpg");
        assertThat(Files.exists(directory.resolve("clients/a/photo.jpg"))).isFalse();
    }

    @Test
    void rejectsPathTraversalAndChecksumMismatch() throws Exception {
        ClientPhotoStorageProperties properties = new ClientPhotoStorageProperties();
        properties.setDirectory(directory);
        LocalClientPhotoStorage storage = new LocalClientPhotoStorage(properties);

        assertThatThrownBy(() -> storage.store("../outside.jpg", new byte[]{1}))
                .isInstanceOf(ClientPhotoStorageException.class);

        storage.store("clients/a/photo.jpg", new byte[]{1, 2, 3});
        assertThatThrownBy(() -> storage.load(
                "clients/a/photo.jpg", "image/jpeg", "0".repeat(64)))
                .isInstanceOf(ClientPhotoStorageException.class);
    }
}
