package io.github.guillermodubon.coachgym.accesscredential.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialStatus;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialPersistenceCommand;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessCredentialJpaEntityTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-09-13T10:00:00Z");

    @Test
    void newEntityContainsOnlySafeMetadataAndStartsActive() {
        AccessCredentialPersistenceCommand command = command();

        AccessCredentialJpaEntity entity = AccessCredentialJpaEntity.create(command);

        assertThat(entity.toDetails().status()).isEqualTo(AccessCredentialStatus.ACTIVE);
        assertThat(entity.toDetails().clientId()).isEqualTo(command.clientId());
        assertThat(entity.tokenFingerprint()).isEqualTo(command.tokenFingerprint());
        assertThat(entity.toStoredDocument().storageKey()).isEqualTo(command.storageKey());
        assertThat(entity.toStoredDocument().checksumSha256()).isEqualTo(command.checksumSha256());
        assertThat(AccessCredentialJpaEntity.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .noneMatch(name -> name.matches("(?i).*(raw|plain).*token.*"));
    }

    @Test
    void revocationSetsFinalMetadataAndCannotBeRepeated() {
        AccessCredentialJpaEntity entity = AccessCredentialJpaEntity.create(command());
        UUID actorId = UUID.randomUUID();
        Instant revokedAt = ISSUED_AT.plusSeconds(60);

        entity.revoke(" Lost card ", actorId, revokedAt);

        assertThat(entity.toDetails())
                .satisfies(details -> {
                    assertThat(details.status()).isEqualTo(AccessCredentialStatus.REVOKED);
                    assertThat(details.revokedAt()).isEqualTo(revokedAt);
                    assertThat(details.revokedByUserId()).isEqualTo(actorId);
                });
        assertThatThrownBy(() -> entity.revoke("Again", actorId, revokedAt))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void replacementLinkIsAllowedOnlyOnceOnFinalCredential() {
        AccessCredentialJpaEntity entity = AccessCredentialJpaEntity.create(command());
        entity.revoke("Lost card", UUID.randomUUID(), ISSUED_AT.plusSeconds(1));
        UUID replacementId = UUID.randomUUID();

        entity.attachReplacement(replacementId);

        assertThat(entity.toDetails().replacedByCredentialId()).isEqualTo(replacementId);
        assertThatThrownBy(() -> entity.attachReplacement(UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }

    private static AccessCredentialPersistenceCommand command() {
        UUID id = UUID.randomUUID();
        return new AccessCredentialPersistenceCommand(
                id,
                UUID.randomUUID(),
                "CRED-ENTITY-1",
                "a".repeat(64),
                "sha256-v1",
                "v1",
                ISSUED_AT,
                UUID.randomUUID(),
                "access-credentials/" + id + ".png",
                "image/png",
                128,
                "b".repeat(64),
                "qr-v1");
    }
}
