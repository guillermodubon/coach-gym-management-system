package io.github.guillermodubon.coachgym.maintenance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.maintenance.IncidentPriority;
import io.github.guillermodubon.coachgym.maintenance.application.command.ChangeIncidentPriorityCommand;
import io.github.guillermodubon.coachgym.maintenance.application.command.ReportIncidentCommand;
import io.github.guillermodubon.coachgym.maintenance.application.command.ResolveIncidentCommand;
import io.github.guillermodubon.coachgym.maintenance.application.command.StartIncidentInvestigationCommand;
import io.github.guillermodubon.coachgym.maintenance.domain.IncidentValidationException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IncidentCommandTest {

    private static final UUID INCIDENT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID EQUIPMENT_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Test
    void reportCommandNormalizesDefinition() {
        ReportIncidentCommand command = new ReportIncidentCommand(
                EQUIPMENT_ID,
                IncidentPriority.HIGH,
                "  Belt stops unexpectedly.  ",
                true,
                4L);

        assertThat(command.description())
                .isEqualTo("Belt stops unexpectedly.");
        assertThat(command.definition().equipmentId())
                .isEqualTo(EQUIPMENT_ID);
    }

    @Test
    void startCommandNormalizesReason() {
        StartIncidentInvestigationCommand command =
                new StartIncidentInvestigationCommand(
                        INCIDENT_ID,
                        "  Investigation started.  ",
                        0L);

        assertThat(command.reason())
                .isEqualTo("Investigation started.");
    }

    @Test
    void priorityCommandNormalizesReason() {
        ChangeIncidentPriorityCommand command =
                new ChangeIncidentPriorityCommand(
                        INCIDENT_ID,
                        IncidentPriority.CRITICAL,
                        "  Immediate safety risk.  ",
                        1L);

        assertThat(command.reason())
                .isEqualTo("Immediate safety risk.");
    }

    @Test
    void resolveCommandNormalizesNotes() {
        ResolveIncidentCommand command =
                new ResolveIncidentCommand(
                        INCIDENT_ID,
                        "  Issue reviewed and resolved.  ",
                        2L);

        assertThat(command.resolutionNotes())
                .isEqualTo("Issue reviewed and resolved.");
    }

    @Test
    void commandsRejectNegativeVersion() {
        assertThatThrownBy(() ->
                new ResolveIncidentCommand(
                        INCIDENT_ID,
                        "Resolved.",
                        -1L))
                .isInstanceOf(IncidentValidationException.class)
                .hasMessage("Incident version cannot be negative.");
    }
}
