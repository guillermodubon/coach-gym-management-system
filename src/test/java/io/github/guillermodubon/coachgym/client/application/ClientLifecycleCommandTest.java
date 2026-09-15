package io.github.guillermodubon.coachgym.client.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ClientLifecycleCommandTest {

    @Test
    void normalizesLifecycleReasonsAndPreservesVersion() {
        DeactivateClientCommand deactivate =
                new DeactivateClientCommand(" Duplicate record ", 2L);
        ReactivateClientCommand reactivate =
                new ReactivateClientCommand(" Administrative review completed ", 3L);

        assertThat(deactivate.reason()).isEqualTo("Duplicate record");
        assertThat(deactivate.expectedVersion()).isEqualTo(2L);
        assertThat(reactivate.reason())
                .isEqualTo("Administrative review completed");
        assertThat(reactivate.expectedVersion()).isEqualTo(3L);
    }

    @Test
    void rejectsBlankReasonAndNegativeVersion() {
        assertThatThrownBy(() -> new DeactivateClientCommand(" ", 0L))
                .isInstanceOf(ClientValidationException.class);
        assertThatThrownBy(() -> new ReactivateClientCommand("Reason", -1L))
                .isInstanceOf(ClientValidationException.class);
    }
}
