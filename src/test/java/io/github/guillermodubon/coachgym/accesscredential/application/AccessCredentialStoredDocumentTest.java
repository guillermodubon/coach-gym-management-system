package io.github.guillermodubon.coachgym.accesscredential.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessCredentialStoredDocumentTest {

    @Test
    void normalizesMetadataAndRedactsStorageIdentityFromDiagnostics() {
        String key = AccessCredentialStorageKey.forCredential(UUID.randomUUID());
        String checksum = "a".repeat(64);

        AccessCredentialStoredDocument document = new AccessCredentialStoredDocument(
                "  " + key + "  ", " IMAGE/PNG ", 256, checksum, " qr-zxing-v1 ");

        assertThat(document.storageKey()).isEqualTo(key);
        assertThat(document.contentType()).isEqualTo("image/png");
        assertThat(document.rendererVersion()).isEqualTo("qr-zxing-v1");
        assertThat(document.toString())
                .doesNotContain(key, checksum)
                .contains("storageKeyPresent=true", "checksumPresent=true");
    }

    @Test
    void rejectsInvalidStorageIdentityAndArtifactMetadata() {
        assertThatThrownBy(() -> new AccessCredentialStoredDocument(
                "../outside.png", "image/png", 1, "a".repeat(64), "qr-zxing-v1"))
                .isInstanceOf(AccessCredentialStorageException.class)
                .hasMessage("Invalid access credential storage key.");
        assertThatThrownBy(() -> new AccessCredentialStoredDocument(
                AccessCredentialStorageKey.forCredential(UUID.randomUUID()),
                "image/jpeg", 1, "a".repeat(64), "qr-zxing-v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Access credential document content type must be image/png.");
        assertThatThrownBy(() -> new AccessCredentialStoredDocument(
                AccessCredentialStorageKey.forCredential(UUID.randomUUID()),
                "image/png", 1, "A".repeat(64), "qr-zxing-v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Access credential document checksum is invalid.");
    }
}
