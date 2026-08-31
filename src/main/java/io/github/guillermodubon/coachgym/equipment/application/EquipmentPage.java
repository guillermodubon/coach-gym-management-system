package io.github.guillermodubon.coachgym.equipment.application;

import io.github.guillermodubon.coachgym.equipment.EquipmentDetails;
import java.util.List;

/**
 * Paginated result of an equipment list query.
 *
 * <p>The {@code items} list is defensively copied to an unmodifiable list on
 * construction.
 */
public record EquipmentPage(
        List<EquipmentDetails> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public EquipmentPage {
        items = List.copyOf(items);
    }
}
