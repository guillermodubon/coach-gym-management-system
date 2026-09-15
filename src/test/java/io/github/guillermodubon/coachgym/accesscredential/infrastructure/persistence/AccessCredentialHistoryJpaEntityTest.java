package io.github.guillermodubon.coachgym.accesscredential.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialHistoryDetails;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialStatus;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialHistoryPersistenceCommand;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessCredentialHistoryJpaEntityTest {

    @Test
    void historyEntityMapsInitialIssueWithoutReason() {
        AccessCredentialHistoryJpaEntity entity = AccessCredentialHistoryJpaEntity.create(
                new AccessCredentialHistoryPersistenceCommand(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        null,
                        AccessCredentialStatus.ACTIVE,
                        null,
                        Instant.parse("2026-09-13T10:00:00Z"),
                        UUID.randomUUID(),
                        null));

        AccessCredentialHistoryDetails details = entity.toDetails();
        assertThat(details.previousStatus()).isNull();
        assertThat(details.newStatus()).isEqualTo(AccessCredentialStatus.ACTIVE);
        assertThat(details.reason()).isNull();
    }

    @Test
    void historyEntityRejectsUnsupportedTransitionBeforeDatabase() {
        assertThatThrownBy(() -> new AccessCredentialHistoryPersistenceCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AccessCredentialStatus.REVOKED,
                AccessCredentialStatus.ACTIVE,
                "Reactivate",
                Instant.parse("2026-09-13T10:00:00Z"),
                UUID.randomUUID(),
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported status transition");
    }
}
