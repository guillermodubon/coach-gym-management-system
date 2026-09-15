package io.github.guillermodubon.coachgym.client.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.client.ClientStatus;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Guards the privacy-safe audit event published by client profile operations.
 */
class ClientProfileAuditEventContractTest {

    @Test
    void profileChangeEventContainsOnlyOperationalAuditMetadata() {
        assertThat(ClientProfileChangedEvent.class.isRecord())
                .isTrue();

        Set<String> componentNames = Arrays.stream(
                        ClientProfileChangedEvent.class
                                .getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        assertThat(componentNames)
                .contains(
                        "clientId",
                        "changeType",
                        "previousStatus",
                        "newStatus",
                        "changedByUserId",
                        "occurredAt")
                .doesNotContain(
                        "email",
                        "phone",
                        "firstName",
                        "lastName",
                        "dateOfBirth",
                        "photo",
                        "photoContent",
                        "storageKey",
                        "token",
                        "password");
    }

    @Test
    void lifecycleEventCanRepresentDeactivationWithoutPersonalData() {
        ClientProfileChangedEvent event =
                new ClientProfileChangedEvent(
                        UUID.randomUUID(),
                        ClientProfileChangedEvent.ChangeType.DEACTIVATED,
                        ClientStatus.ACTIVE,
                        ClientStatus.INACTIVE,
                        UUID.randomUUID(),
                        Instant.parse(
                                "2026-09-09T12:00:00Z"));

        assertThat(event.changeType())
                .isEqualTo(
                        ClientProfileChangedEvent.ChangeType.DEACTIVATED);

        assertThat(event.previousStatus())
                .isEqualTo(ClientStatus.ACTIVE);

        assertThat(event.newStatus())
                .isEqualTo(ClientStatus.INACTIVE);

        assertThat(event.clientId())
                .isNotNull();

        assertThat(event.changedByUserId())
                .isNotNull();

        assertThat(event.occurredAt())
                .isNotNull();
    }
}