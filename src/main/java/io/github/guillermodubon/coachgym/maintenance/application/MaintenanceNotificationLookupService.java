package io.github.guillermodubon.coachgym.maintenance.application;

import io.github.guillermodubon.coachgym.maintenance.MaintenanceDetails;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceNotificationDetails;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceNotificationLookup;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Exposes the minimum public data needed to route maintenance notifications. */
@Service
class MaintenanceNotificationLookupService implements MaintenanceNotificationLookup {

    private final MaintenanceStore maintenanceStore;

    MaintenanceNotificationLookupService(MaintenanceStore maintenanceStore) {
        this.maintenanceStore = maintenanceStore;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MaintenanceNotificationDetails> findById(UUID maintenanceId) {
        if (maintenanceId == null) {
            return Optional.empty();
        }
        return maintenanceStore.findById(maintenanceId)
                .map(MaintenanceNotificationLookupService::toNotificationDetails);
    }

    private static MaintenanceNotificationDetails toNotificationDetails(
            MaintenanceDetails details) {
        return new MaintenanceNotificationDetails(
                details.id(),
                details.maintenanceCode(),
                details.equipmentId(),
                details.equipmentCode(),
                details.createdByUserId(),
                details.assignedToUserId());
    }
}
