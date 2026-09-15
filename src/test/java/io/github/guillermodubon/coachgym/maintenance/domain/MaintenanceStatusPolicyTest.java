package io.github.guillermodubon.coachgym.maintenance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.maintenance.MaintenanceStatus;
import org.junit.jupiter.api.Test;

class MaintenanceStatusPolicyTest {

    private final MaintenanceStatusPolicy policy = new MaintenanceStatusPolicy();

    @Test
    void allowsOnlyApprovedTransitions() {
        assertThat(policy.isAllowed(MaintenanceStatus.SCHEDULED,
                MaintenanceStatus.IN_PROGRESS)).isTrue();
        assertThat(policy.isAllowed(MaintenanceStatus.SCHEDULED,
                MaintenanceStatus.CANCELLED)).isTrue();
        assertThat(policy.isAllowed(MaintenanceStatus.IN_PROGRESS,
                MaintenanceStatus.COMPLETED)).isTrue();
        assertThat(policy.isAllowed(MaintenanceStatus.IN_PROGRESS,
                MaintenanceStatus.CANCELLED)).isTrue();
    }

    @Test
    void rejectsSkippedBackwardAndTerminalTransitions() {
        assertThatThrownBy(() -> policy.complete(
                MaintenanceStatus.SCHEDULED, "Skip."))
                .isInstanceOf(MaintenanceValidationException.class)
                .hasMessageContaining("SCHEDULED to COMPLETED");
        assertThatThrownBy(() -> policy.transition(
                MaintenanceStatus.IN_PROGRESS, MaintenanceStatus.SCHEDULED,
                "Go back."))
                .isInstanceOf(MaintenanceValidationException.class);
        assertThatThrownBy(() -> policy.start(
                MaintenanceStatus.COMPLETED, "Restart."))
                .isInstanceOf(MaintenanceValidationException.class);
        assertThatThrownBy(() -> policy.start(
                MaintenanceStatus.CANCELLED, "Restart."))
                .isInstanceOf(MaintenanceValidationException.class);
    }

    @Test
    void returnsNormalizedTransition() {
        MaintenanceStatusTransition transition = policy.start(
                MaintenanceStatus.SCHEDULED, "  Work started.  ");
        assertThat(transition.previousStatus()).isEqualTo(MaintenanceStatus.SCHEDULED);
        assertThat(transition.resultingStatus()).isEqualTo(MaintenanceStatus.IN_PROGRESS);
        assertThat(transition.reason()).isEqualTo("Work started.");
    }
}
