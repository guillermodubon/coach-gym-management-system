package io.github.guillermodubon.coachgym.maintenance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import io.github.guillermodubon.coachgym.maintenance.IncidentStatus;
import org.junit.jupiter.api.Test;

class IncidentStatusTransitionTest {
    @Test void createsNormalizedTransition() {
        var transition = new IncidentStatusTransition(IncidentStatus.OPEN, IncidentStatus.IN_PROGRESS, "  Started.  ");
        assertThat(transition.reason()).isEqualTo("Started.");
    }
    @Test void rejectsSelfTransition() {
        assertThatThrownBy(() -> new IncidentStatusTransition(IncidentStatus.OPEN, IncidentStatus.OPEN, "No change."))
                .isInstanceOf(IncidentValidationException.class).hasMessageContaining("current status");
    }
    @Test void rejectsBlankReason() {
        assertThatThrownBy(() -> new IncidentStatusTransition(IncidentStatus.OPEN, IncidentStatus.IN_PROGRESS, " "))
                .isInstanceOf(IncidentValidationException.class).hasMessage("Incident transition reason is required.");
    }
}
