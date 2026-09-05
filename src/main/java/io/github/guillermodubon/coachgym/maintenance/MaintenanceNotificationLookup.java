package io.github.guillermodubon.coachgym.maintenance;

import java.util.Optional;
import java.util.UUID;

/** Public maintenance-module contract for notification recipient routing. */
public interface MaintenanceNotificationLookup {

    Optional<MaintenanceNotificationDetails> findById(UUID maintenanceId);
}
