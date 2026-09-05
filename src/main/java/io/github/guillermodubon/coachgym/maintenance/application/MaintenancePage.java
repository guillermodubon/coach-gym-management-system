package io.github.guillermodubon.coachgym.maintenance.application;

import io.github.guillermodubon.coachgym.maintenance.MaintenanceDetails;
import io.github.guillermodubon.coachgym.maintenance.domain.MaintenanceValidationException;
import java.util.List;

/** Immutable paginated maintenance-work-order result. */
public record MaintenancePage(
        List<MaintenanceDetails> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public MaintenancePage {
        items = items == null ? List.of() : List.copyOf(items);
        if (page < 0) {
            throw new MaintenanceValidationException(
                    "Maintenance page index must not be negative.");
        }
        if (size < 1) {
            throw new MaintenanceValidationException(
                    "Maintenance page size must be positive.");
        }
        if (totalElements < 0 || totalPages < 0) {
            throw new MaintenanceValidationException(
                    "Maintenance page totals must not be negative.");
        }
        int expectedPages = totalElements == 0
                ? 0 : (int) Math.ceil((double) totalElements / size);
        if (totalPages != expectedPages) {
            throw new MaintenanceValidationException(
                    "Maintenance page totals are inconsistent.");
        }
        if (items.size() > size || items.size() > totalElements) {
            throw new MaintenanceValidationException(
                    "Maintenance page items are inconsistent with page metadata.");
        }
    }
}
