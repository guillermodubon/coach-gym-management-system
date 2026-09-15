package io.github.guillermodubon.coachgym.maintenance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import io.github.guillermodubon.coachgym.maintenance.IncidentPriority;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IncidentDefinitionTest {
    private static final UUID EQUIPMENT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    @Test void createsNormalizedDefinition() {
        var d = new IncidentDefinition(EQUIPMENT_ID, IncidentPriority.HIGH, "  Treadmill stops unexpectedly.  ", true, 3L);
        assertThat(d.description()).isEqualTo("Treadmill stops unexpectedly.");
        assertThat(d.equipmentVersion()).isEqualTo(3L);
    }
    @Test void allowsReportWithoutWithdrawal() {
        var d = new IncidentDefinition(EQUIPMENT_ID, IncidentPriority.LOW, "Minor noise.", false, null);
        assertThat(d.equipmentVersion()).isNull();
    }
    @Test void rejectsBlankDescription() {
        assertThatThrownBy(() -> new IncidentDefinition(EQUIPMENT_ID, IncidentPriority.HIGH, " ", false, null))
                .isInstanceOf(IncidentValidationException.class).hasMessage("Incident description is required.");
    }
    @Test void rejectsLongDescription() {
        String text = "x".repeat(IncidentDefinition.MAX_DESCRIPTION_LENGTH + 1);
        assertThatThrownBy(() -> new IncidentDefinition(EQUIPMENT_ID, IncidentPriority.HIGH, text, false, null))
                .isInstanceOf(IncidentValidationException.class).hasMessageContaining("must not exceed");
    }
    @Test void requiresVersionForWithdrawal() {
        assertThatThrownBy(() -> new IncidentDefinition(EQUIPMENT_ID, IncidentPriority.CRITICAL, "Risk.", true, null))
                .isInstanceOf(IncidentValidationException.class).hasMessageContaining("Equipment version is required");
    }
    @Test void rejectsNegativeVersion() {
        assertThatThrownBy(() -> new IncidentDefinition(EQUIPMENT_ID, IncidentPriority.HIGH, "Risk.", true, -1L))
                .isInstanceOf(IncidentValidationException.class).hasMessage("Equipment version cannot be negative.");
    }
}
