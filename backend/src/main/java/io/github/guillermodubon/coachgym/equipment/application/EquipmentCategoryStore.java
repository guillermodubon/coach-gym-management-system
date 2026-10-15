package io.github.guillermodubon.coachgym.equipment.application;

import io.github.guillermodubon.coachgym.equipment.EquipmentCategoryDetails;
import io.github.guillermodubon.coachgym.equipment.domain.EquipmentCategoryDefinition;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for equipment categories.
 *
 * <p>Implementations live in the {@code infrastructure.persistence} package
 * and must not be imported by controllers or domain objects.</p>
 */
public interface EquipmentCategoryStore {

    /**
     * Persists a new equipment category.
     *
     * @param id         application-generated category identifier
     * @param definition validated category definition
     * @param occurredAt operation timestamp used for creation and update fields
     * @return persisted category details
     */
    EquipmentCategoryDetails create(
            UUID id,
            EquipmentCategoryDefinition definition,
            Instant occurredAt);

    /**
     * Updates the mutable fields of an existing category using optimistic
     * locking.
     *
     * @param categoryId category identifier
     * @param definition validated updated definition
     * @param version    expected category version
     * @param occurredAt operation timestamp used for the update field
     * @return updated category details
     */
    EquipmentCategoryDetails update(
            UUID categoryId,
            EquipmentCategoryDefinition definition,
            long version,
            Instant occurredAt);

    /**
     * Activates an equipment category using optimistic locking.
     *
     * @param categoryId category identifier
     * @param version    expected category version
     * @param occurredAt operation timestamp used for the update field
     * @return activated category details
     */
    EquipmentCategoryDetails activate(
            UUID categoryId,
            long version,
            Instant occurredAt);

    /**
     * Deactivates an equipment category using optimistic locking.
     *
     * @param categoryId category identifier
     * @param version    expected category version
     * @param occurredAt operation timestamp used for the update field
     * @return deactivated category details
     */
    EquipmentCategoryDetails deactivate(
            UUID categoryId,
            long version,
            Instant occurredAt);

    /**
     * Finds an equipment category by its identifier.
     *
     * @param categoryId category identifier
     * @return category details, or empty when no category exists
     */
    Optional<EquipmentCategoryDetails> findById(
            UUID categoryId);

    /**
     * Returns a paginated category list matching the supplied query.
     *
     * @param query validated category search query
     * @return paginated category details
     */
    EquipmentCategoryPage findAll(
            EquipmentCategorySearchQuery query);

    /**
     * Checks whether another category uses the supplied name.
     *
     * <p>The comparison is case-insensitive. {@code excludeId} is null during
     * creation and contains the currently updated category during an
     * update.</p>
     *
     * @param name      normalized category name
     * @param excludeId category identifier excluded from the lookup
     * @return true when a duplicate exists
     */
    boolean existsByNameIgnoreCase(
            String name,
            UUID excludeId);
}
