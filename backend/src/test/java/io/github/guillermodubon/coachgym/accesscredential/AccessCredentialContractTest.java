package io.github.guillermodubon.coachgym.accesscredential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessCredentialContractTest {

    private static final UUID CREDENTIAL_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000801");
    private static final UUID CLIENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000802");
    private static final UUID ACTOR_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000803");
    private static final Instant ISSUED_AT = Instant.parse("2026-09-12T10:00:00Z");

    @Test
    void detailsNormalizeSafeMetadataAndRejectSecretLikeState() {
        AccessCredentialDetails details = new AccessCredentialDetails(
                CREDENTIAL_ID,
                CLIENT_ID,
                " CRED-0001 ",
                AccessCredentialStatus.ACTIVE,
                "v1",
                ISSUED_AT,
                ACTOR_ID,
                null,
                null,
                null,
                0);

        assertThat(details.credentialCode()).isEqualTo("CRED-0001");
        assertThat(details.payloadVersion()).isEqualTo("v1");
        assertThat(details.status()).isEqualTo(AccessCredentialStatus.ACTIVE);
        assertThat(details.toString()).doesNotContain("token", "digest", "storageKey");
    }

    @Test
    void revokedDetailsRequireConsistentFinalizationMetadata() {
        UUID replacementId = UUID.fromString("00000000-0000-0000-0000-000000000804");
        AccessCredentialDetails details = new AccessCredentialDetails(
                CREDENTIAL_ID,
                CLIENT_ID,
                "CRED-0001",
                AccessCredentialStatus.REVOKED,
                "v1",
                ISSUED_AT,
                ACTOR_ID,
                ISSUED_AT.plusSeconds(60),
                ACTOR_ID,
                replacementId,
                1);

        assertThat(details.revokedAt()).isEqualTo(ISSUED_AT.plusSeconds(60));
        assertThat(details.replacedByCredentialId()).isEqualTo(replacementId);

        assertThatThrownBy(() -> new AccessCredentialDetails(
                CREDENTIAL_ID,
                CLIENT_ID,
                "CRED-0001",
                AccessCredentialStatus.REVOKED,
                "v1",
                ISSUED_AT,
                ACTOR_ID,
                null,
                ACTOR_ID,
                null,
                0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("revocation metadata");
    }

    @Test
    void documentNormalizesPngAndDefensivelyCopiesBytes() {
        byte[] source = {1, 2, 3};
        AccessCredentialDocument document = new AccessCredentialDocument(
                " IMAGE/PNG ", source);
        source[0] = 9;

        assertThat(document.contentType()).isEqualTo("image/png");
        assertThat(document.sizeBytes()).isEqualTo(3);
        assertThat(document.bytes()).containsExactly(1, 2, 3);

        byte[] returned = document.bytes();
        returned[1] = 8;
        assertThat(document.bytes()).containsExactly(1, 2, 3);
    }

    @Test
    void historyAllowsIssueAndFinalRevocationOnly() {
        AccessCredentialHistoryDetails issued = new AccessCredentialHistoryDetails(
                UUID.randomUUID(),
                CREDENTIAL_ID,
                CLIENT_ID,
                null,
                AccessCredentialStatus.ACTIVE,
                null,
                ISSUED_AT,
                ACTOR_ID,
                null);
        AccessCredentialHistoryDetails revoked = new AccessCredentialHistoryDetails(
                UUID.randomUUID(),
                CREDENTIAL_ID,
                CLIENT_ID,
                AccessCredentialStatus.ACTIVE,
                AccessCredentialStatus.REVOKED,
                " Lost credential ",
                ISSUED_AT.plusSeconds(60),
                ACTOR_ID,
                UUID.randomUUID());

        assertThat(issued.reason()).isNull();
        assertThat(revoked.reason()).isEqualTo("Lost credential");

        assertThatThrownBy(() -> new AccessCredentialHistoryDetails(
                UUID.randomUUID(),
                CREDENTIAL_ID,
                CLIENT_ID,
                AccessCredentialStatus.REVOKED,
                AccessCredentialStatus.ACTIVE,
                "Reactivated",
                ISSUED_AT.plusSeconds(120),
                ACTOR_ID,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported status transition");
    }
}
