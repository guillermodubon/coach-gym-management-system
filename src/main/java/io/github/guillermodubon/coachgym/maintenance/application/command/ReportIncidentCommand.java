package io.github.guillermodubon.coachgym.maintenance.application.command;

import io.github.guillermodubon.coachgym.maintenance.IncidentPriority;
import io.github.guillermodubon.coachgym.maintenance.domain.IncidentDefinition;
import java.util.UUID;

/** Command used to report a new equipment incident. */
public record ReportIncidentCommand(
        UUID equipmentId,
        IncidentPriority priority,
        String description,
        boolean takeOutOfService,
        Long equipmentVersion) {

    public ReportIncidentCommand {
        IncidentDefinition definition = new IncidentDefinition(
                equipmentId,
                priority,
                description,
                takeOutOfService,
                equipmentVersion);

        equipmentId = definition.equipmentId();
        priority = definition.priority();
        description = definition.description();
        takeOutOfService = definition.takeOutOfService();
        equipmentVersion = definition.equipmentVersion();
    }

    public IncidentDefinition definition() {
        return new IncidentDefinition(
                equipmentId,
                priority,
                description,
                takeOutOfService,
                equipmentVersion);
    }
}
