package io.github.guillermodubon.coachgym.maintenance.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.maintenance.MaintenanceStatus;
import org.junit.jupiter.api.Test;

class MaintenanceStatusTransitionTest {

    @Test
    void rejectsSameStatusAndBlankReason() {
        assertThatThrownBy(() -> new MaintenanceStatusTransition(
                MaintenanceStatus.SCHEDULED,
                MaintenanceStatus.SCHEDULED,
                "No change."))
                .isInstanceOf(MaintenanceValidationException.class);
        assertThatThrownBy(() -> new MaintenanceStatusTransition(
                MaintenanceStatus.SCHEDULED,
                MaintenanceStatus.IN_PROGRESS,
                " "))
                .isInstanceOf(MaintenanceValidationException.class);
    }
}
