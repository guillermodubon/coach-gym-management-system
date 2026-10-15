package io.github.guillermodubon.coachgym.equipment.application;

import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryDetails;
import java.util.List;

/**
 * Paginated result of an equipment category list query.
 *
 * <p>The {@code items} list is defensively copied to an unmodifiable list on
 * construction.
 */
public record EquipmentCategoryPage(
        List<EquipmentCategoryDetails> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public EquipmentCategoryPage {
        items = List.copyOf(items);
    }
}
