package io.github.guillermodubon.coachgym.equipment;

/**
 * Public projection of equipment lifecycle status exposed to other modules.
 *
 * <p>Mirrors {@link io.github.guillermodubon.coachgym.equipment.domain.EquipmentStatus}
 * without leaking the domain package across module boundaries.
 */
public enum EquipmentStatus {
    AVAILABLE,
    MAINTENANCE,
    OUT_OF_SERVICE,
    RETIRED
}
