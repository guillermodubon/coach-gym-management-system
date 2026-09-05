package io.github.guillermodubon.coachgym.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MaintenanceStatusTest {

    @Test
    void exposesOnlyDatabaseSupportedStatuses() {
        assertThat(MaintenanceStatus.values())
                .containsExactly(
                        MaintenanceStatus.SCHEDULED,
                        MaintenanceStatus.IN_PROGRESS,
                        MaintenanceStatus.COMPLETED,
                        MaintenanceStatus.CANCELLED);
    }

    @Test
    void identifiesOnlyCompletedAndCancelledAsTerminal() {
        assertThat(MaintenanceStatus.SCHEDULED.isTerminal()).isFalse();
        assertThat(MaintenanceStatus.IN_PROGRESS.isTerminal()).isFalse();
        assertThat(MaintenanceStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(MaintenanceStatus.CANCELLED.isTerminal()).isTrue();
    }
}
