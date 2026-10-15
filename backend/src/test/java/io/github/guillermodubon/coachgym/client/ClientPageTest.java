package io.github.guillermodubon.coachgym.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClientPageTest {

    @Test
    void defensivelyCopiesItems() {
        ClientSummary summary = summary();
        ClientPage page = new ClientPage(List.of(summary), 0, 25, 1, 1);
        assertThat(page.items()).containsExactly(summary);
        assertThatThrownBy(() -> page.items().add(summary))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInconsistentPageSize() {
        assertThatThrownBy(() -> new ClientPage(
                List.of(summary(), summary()), 0, 1, 2, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ClientSummary summary() {
        return new ClientSummary(
                UUID.randomUUID(), "CLI-000001", "Ana", "Lopez",
                "7000-0000", "ana@example.test", ClientStatus.ACTIVE,
                "ACTIVE", null, false, Instant.parse("2026-09-08T12:00:00Z"), 0);
    }
}
