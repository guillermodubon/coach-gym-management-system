package io.github.guillermodubon.coachgym.accesscredential.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialDocument;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialStorageException;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialStorageKey;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialStoredDocument;
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

class LocalAccessCredentialStorageTest {

    @TempDir
    Path directory;

    @Test
    void generatesCanonicalKeyStoresReadsMetadataAndCleansUp() throws Exception {
        LocalAccessCredentialStorage storage = storage();
        UUID credentialId = UUID.fromString("00000000-0000-0000-0000-000000000940");
        AccessCredentialDocument document = document();
        String key = storage.generateStorageKey(credentialId);

        AccessCredentialStoredDocument stored = storage.store(key, document, "qr-zxing-v1");

        assertThat(key).isEqualTo(
                "access-credentials/00000000-0000-0000-0000-000000000940.png");
        assertThat(stored.storageKey()).isEqualTo(key);
        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.sizeBytes()).isEqualTo(document.sizeBytes());
        assertThat(stored.checksumSha256()).isEqualTo(checksum(document.bytes()));
        assertThat(stored.rendererVersion()).isEqualTo("qr-zxing-v1");
        assertThat(storage.load(key, stored.contentType(), stored.sizeBytes(),
                stored.checksumSha256()).bytes())
                .containsExactly(document.bytes());
        assertThat(Files.exists(directory.resolve(key))).isTrue();

        storage.delete(key);
        storage.delete(key);
        assertThat(Files.exists(directory.resolve(key))).isFalse();
        assertThat(noTemporaryFiles()).isTrue();
    }

    @Test
    void rejectsTraversalAndNonCanonicalKeysWithoutExposingFilesystemPaths() throws Exception {
        LocalAccessCredentialStorage storage = storage();
        AccessCredentialDocument document = document();

        assertThatThrownBy(() -> storage.store("../outside.png", document, "qr-zxing-v1"))
                .isInstanceOf(AccessCredentialStorageException.class)
                .hasMessage("Invalid access credential storage key.")
                .hasMessageNotContaining(directory.toString());
        assertThatThrownBy(() -> storage.store("access-credentials/file.png", document,
                "qr-zxing-v1"))
                .isInstanceOf(AccessCredentialStorageException.class)
                .hasMessage("Invalid access credential storage key.");
        assertThatThrownBy(() -> storage.delete("/absolute/path.png"))
                .isInstanceOf(AccessCredentialStorageException.class)
                .hasMessage("Invalid access credential storage key.");
        assertThat(AccessCredentialStorageKey.isCanonical(
                "access-credentials/00000000-0000-0000-0000-000000000940.png")).isTrue();
    }

    @Test
    void validatesPngMetadataChecksumsAndMissingFilesSafely() throws Exception {
        LocalAccessCredentialStorage storage = storage();
        AccessCredentialDocument document = document();
        String key = storage.generateStorageKey(UUID.randomUUID());
        AccessCredentialStoredDocument stored = storage.store(key, document, null);

        assertThat(stored.rendererVersion()).isNull();
        assertThatThrownBy(() -> storage.load(key, "image/jpeg", stored.sizeBytes(),
                stored.checksumSha256()))
                .isInstanceOf(AccessCredentialStorageException.class)
                .hasMessage("Stored access credential content type is invalid.");
        assertThatThrownBy(() -> storage.load(key, stored.contentType(), stored.sizeBytes(),
                "0".repeat(64)))
                .isInstanceOf(AccessCredentialStorageException.class)
                .hasMessage("Stored access credential document checksum does not match.");

        storage.delete(key);
        assertThatThrownBy(() -> storage.load(key, stored.contentType(), stored.sizeBytes(),
                stored.checksumSha256()))
                .isInstanceOf(AccessCredentialStorageException.class)
                .hasMessage("Access credential document could not be found.")
                .hasMessageNotContaining(directory.toString());
    }

    @Test
    void rejectsNonPngContentAndDoesNotOverwriteAnExistingArtifact() throws Exception {
        LocalAccessCredentialStorage storage = storage();
        UUID credentialId = UUID.randomUUID();
        String key = storage.generateStorageKey(credentialId);
        AccessCredentialDocument nonPng = new AccessCredentialDocument(
                "image/png", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> storage.store(key, nonPng, "qr-zxing-v1"))
                .isInstanceOf(AccessCredentialStorageException.class)
                .hasMessage("Access credential document is not a PNG.");

        AccessCredentialDocument document = document();
        storage.store(key, document, "qr-zxing-v1");
        assertThatThrownBy(() -> storage.store(key, document, "qr-zxing-v1"))
                .isInstanceOf(AccessCredentialStorageException.class)
                .hasMessage("Access credential artifact already exists.");
        assertThat(noTemporaryFiles()).isTrue();
    }

    private LocalAccessCredentialStorage storage() {
        AccessCredentialStorageProperties properties = new AccessCredentialStorageProperties();
        properties.setDirectory(directory);
        return new LocalAccessCredentialStorage(properties);
    }

    private static AccessCredentialDocument document() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", output);
        return new AccessCredentialDocument("image/png", output.toByteArray());
    }

    private static String checksum(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private boolean noTemporaryFiles() throws Exception {
        try (var paths = Files.walk(directory)) {
            return paths.noneMatch(path -> path.getFileName().toString().startsWith(".credential-"));
        }
    }
}
