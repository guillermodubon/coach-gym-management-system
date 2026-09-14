package io.github.guillermodubon.coachgym.accesscredential.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessCredentialPersistenceCommandTest {

    private static final UUID ID = UUID.randomUUID();
    private static final Instant ISSUED_AT = Instant.parse("2026-09-13T10:00:00Z");

    @Test
    void commandNormalizesArtifactMetadataAndRedactsProtectedValuesFromToString() {
        AccessCredentialPersistenceCommand command = command(
                "  image/PNG  ",
                "  QR renderer v1  ");

        assertThat(command.contentType()).isEqualTo("image/png");
        assertThat(command.rendererVersion()).isEqualTo("QR renderer v1");
        assertThat(command.toString())
                .doesNotContain(command.tokenFingerprint())
                .doesNotContain(command.checksumSha256())
                .contains("tokenFingerprintPresent=true")
                .contains("checksumPresent=true");
    }

    @Test
    void commandRejectsInvalidProtectedMetadata() {
        assertThatThrownBy(() -> command("image/jpeg", "renderer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content type");
        assertThatThrownBy(() -> new AccessCredentialPersistenceCommand(
                ID,
                UUID.randomUUID(),
                "CRED-1",
                "A".repeat(64),
                "sha256-v1",
                "v1",
                ISSUED_AT,
                UUID.randomUUID(),
                "access-credentials/" + ID + ".png",
                "image/png",
                128,
                "b".repeat(64),
                "renderer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fingerprint");
    }

    private static AccessCredentialPersistenceCommand command(
            String contentType,
            String rendererVersion) {
        return new AccessCredentialPersistenceCommand(
                ID,
                UUID.randomUUID(),
                " CRED-1 ",
                "a".repeat(64),
                "sha256-v1",
                "v1",
                ISSUED_AT,
                UUID.randomUUID(),
                "access-credentials/" + ID + ".png",
                contentType,
                128,
                "b".repeat(64),
                rendererVersion);
    }
}
