package io.github.guillermodubon.coachgym.accesscredential.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialDetails;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialDocument;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialEmailSourceQuery;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialStatus;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialQuery;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialStorage;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialStoredDocument;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JpaAccessCredentialEmailSourceQueryTest {

    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");

    @Mock private AccessCredentialQuery credentialQuery;
    @Mock private AccessCredentialStorage credentialStorage;

    @Test
    void combinesActiveCredentialMetadataWithTheCanonicalStoredPng() {
        UUID credentialId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        byte[] bytes = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        String checksum = checksum(bytes);
        AccessCredentialDetails details = new AccessCredentialDetails(
                credentialId, clientId, "QR-000001", AccessCredentialStatus.ACTIVE,
                "v1", NOW, UUID.randomUUID(), null, null, null, 0);
        AccessCredentialStoredDocument metadata = new AccessCredentialStoredDocument(
                "access-credentials/" + credentialId + ".png", "image/png", bytes.length,
                checksum, "qr-v1");
        when(credentialQuery.findById(credentialId)).thenReturn(Optional.of(details));
        when(credentialQuery.findArtifactByCredentialId(credentialId))
                .thenReturn(Optional.of(metadata));
        when(credentialStorage.load(
                metadata.storageKey(), metadata.contentType(), metadata.sizeBytes(), metadata.checksumSha256()))
                .thenReturn(new AccessCredentialDocument("image/png", bytes));

        var source = new JpaAccessCredentialEmailSourceQuery(
                credentialQuery, credentialStorage).findByCredentialId(credentialId);

        assertThat(source).isPresent();
        assertThat(source.orElseThrow().clientId()).isEqualTo(clientId);
        assertThat(source.orElseThrow().document().bytes()).isEqualTo(bytes);
    }

    @Test
    void excludesRevokedCredentialsFromDelivery() {
        UUID credentialId = UUID.randomUUID();
        AccessCredentialDetails revoked = new AccessCredentialDetails(
                credentialId, UUID.randomUUID(), "QR-000001", AccessCredentialStatus.REVOKED,
                "v1", NOW.minusSeconds(60), UUID.randomUUID(), NOW, UUID.randomUUID(), null, 0);
        when(credentialQuery.findById(credentialId)).thenReturn(Optional.of(revoked));

        assertThat(new JpaAccessCredentialEmailSourceQuery(
                credentialQuery, credentialStorage).findByCredentialId(credentialId))
                .isEmpty();
    }

    private static String checksum(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
