package io.github.guillermodubon.coachgym.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NotificationSeverityTest {

    @Test
    void exposesSupportedOperationalSeverities() {
        assertThat(NotificationSeverity.values())
                .containsExactly(
                        NotificationSeverity.INFO,
                        NotificationSeverity.WARNING,
                        NotificationSeverity.CRITICAL);
    }
}
