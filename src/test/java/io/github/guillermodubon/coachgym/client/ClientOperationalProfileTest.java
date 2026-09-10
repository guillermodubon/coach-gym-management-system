package io.github.guillermodubon.coachgym.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClientOperationalProfileTest {

    @Test
    void acceptsProfileWithoutOptionalOperationalSections() {
        ClientOperationalProfile profile = new ClientOperationalProfile(
                UUID.randomUUID(), "CLI-000001", "Ana", "Lopez",
                null, "7000-0000", null, ClientStatus.ACTIVE,
                null, null, null, null, null,
                Instant.parse("2026-09-01T12:00:00Z"),
                Instant.parse("2026-09-08T12:00:00Z"), 0);

        assertThat(profile.fullName()).isEqualTo("Ana Lopez");
        assertThat(profile.hasCurrentMembership()).isFalse();
        assertThat(profile.hasPhoto()).isFalse();
    }

    @Test
    void rejectsInvalidTimestampsAndVersion() {
        Instant created = Instant.parse("2026-09-08T12:00:00Z");
        assertThatThrownBy(() -> new ClientOperationalProfile(
                UUID.randomUUID(), "CLI-000001", "Ana", "Lopez",
                null, "7000-0000", null, ClientStatus.ACTIVE,
                null, null, null, null, null,
                created, created.minusSeconds(1), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
