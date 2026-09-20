package io.github.guillermodubon.coachgym.user.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.user.application.StaffProfilePhotoContent;
import io.github.guillermodubon.coachgym.user.application.StaffProfilePhotoStorageException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalStaffProfilePhotoStorageTest {

    private static final UUID USER_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");

    @TempDir
    Path directory;

    @Test
    void storesLoadsAndDeletesOnlyCanonicalPrivateObjects() throws Exception {
        LocalStaffProfilePhotoStorage storage = storage();
        StaffProfilePhotoContent content = pngContent();
        String key = storage.generateStorageKey(USER_ID, content.contentType());

        storage.store(key, content);
        StaffProfilePhotoContent loaded = storage.load(
                key, content.contentType(), content.sizeBytes(), content.checksumSha256());

        assertThat(loaded.bytes()).containsExactly(content.bytes());
        assertThat(Files.exists(directory.resolve(key))).isTrue();

        storage.delete(key);
        storage.delete(key);
        assertThat(Files.exists(directory.resolve(key))).isFalse();
        assertThat(noTemporaryFiles()).isTrue();
    }

    @Test
    void rejectsInvalidSignatureTypeMismatchTraversalChecksumAndOverwrite() throws Exception {
        LocalStaffProfilePhotoStorage storage = storage();
        StaffProfilePhotoContent content = pngContent();
        String key = storage.generateStorageKey(USER_ID, content.contentType());

        byte[] invalidBytes = {1, 2, 3};
        StaffProfilePhotoContent invalid = new StaffProfilePhotoContent(
                "image/png", invalidBytes, checksum(invalidBytes));
        assertThatThrownBy(() -> storage.store(key, invalid))
                .isInstanceOf(StaffProfilePhotoStorageException.class)
                .hasMessage("Staff profile photo is invalid.");

        assertThatThrownBy(() -> storage.store("../outside.png", content))
                .isInstanceOf(StaffProfilePhotoStorageException.class)
                .hasMessageNotContaining(directory.toString());

        storage.store(key, content);
        assertThatThrownBy(() -> storage.store(key, content))
                .isInstanceOf(StaffProfilePhotoStorageException.class)
                .hasMessage("Staff profile photo artifact already exists.");
        assertThatThrownBy(() -> storage.load(
                key, content.contentType(), content.sizeBytes(), "0".repeat(64)))
                .isInstanceOf(StaffProfilePhotoStorageException.class)
                .hasMessage("Stored staff profile photo is invalid.");
    }

    @Test
    void rejectsMissingArtifactsAndLeavesNoGeneratedDataOutsideTempRoot() throws Exception {
        LocalStaffProfilePhotoStorage storage = storage();
        StaffProfilePhotoContent content = pngContent();
        String key = storage.generateStorageKey(USER_ID, content.contentType());

        assertThatThrownBy(() -> storage.load(
                key, content.contentType(), content.sizeBytes(), content.checksumSha256()))
                .isInstanceOf(StaffProfilePhotoStorageException.class)
                .hasMessage("Staff profile photo could not be found.")
                .hasMessageNotContaining(directory.toString());
        assertThat(noTemporaryFiles()).isTrue();
    }

    private LocalStaffProfilePhotoStorage storage() {
        StaffProfilePhotoStorageProperties properties =
                new StaffProfilePhotoStorageProperties();
        properties.setDirectory(directory);
        return new LocalStaffProfilePhotoStorage(properties);
    }

    private static StaffProfilePhotoContent pngContent() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", output);
        byte[] bytes = output.toByteArray();
        return new StaffProfilePhotoContent("image/png", bytes, checksum(bytes));
    }

    private static String checksum(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private boolean noTemporaryFiles() throws Exception {
        if (!Files.exists(directory)) {
            return true;
        }
        try (var paths = Files.walk(directory)) {
            return paths.noneMatch(path -> path.getFileName().toString()
                    .startsWith(".staff-photo-"));
        }
    }
}
