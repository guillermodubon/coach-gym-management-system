package io.github.guillermodubon.coachgym.maintenance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import io.github.guillermodubon.coachgym.maintenance.IncidentStatus;
import org.junit.jupiter.api.Test;

class IncidentStatusPolicyTest {
    private final IncidentStatusPolicy policy = new IncidentStatusPolicy();
    @Test void allowsOpenToInProgress() {
        assertThat(policy.startInvestigation(IncidentStatus.OPEN, "Started.").resultingStatus())
                .isEqualTo(IncidentStatus.IN_PROGRESS);
    }
    @Test void allowsInProgressToResolved() {
        assertThat(policy.resolve(IncidentStatus.IN_PROGRESS, "Resolved.").resultingStatus())
                .isEqualTo(IncidentStatus.RESOLVED);
    }
    @Test void rejectsOpenToResolved() {
        assertThatThrownBy(() -> policy.validate(IncidentStatus.OPEN, IncidentStatus.RESOLVED, "Skip."))
                .isInstanceOf(IncidentValidationException.class).hasMessageContaining("OPEN to RESOLVED is not allowed");
    }
    @Test void rejectsTransitionFromResolved() {
        assertThatThrownBy(() -> policy.validate(IncidentStatus.RESOLVED, IncidentStatus.IN_PROGRESS, "Reopen."))
                .isInstanceOf(IncidentValidationException.class).hasMessage("A resolved incident is terminal.");
    }
}
