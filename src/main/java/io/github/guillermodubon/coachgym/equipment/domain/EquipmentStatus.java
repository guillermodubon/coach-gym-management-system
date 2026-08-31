package io.github.guillermodubon.coachgym.equipment.domain;

/**
 * Lifecycle status for a piece of equipment.
 *
 * <p>Values mirror the {@code ck_equipment_status} check constraint in
 * {@code V8__create_equipment_operations.sql}.
 *
 * <ul>
 *   <li>{@link #AVAILABLE} – ready for use; initial status on registration.</li>
 *   <li>{@link #MAINTENANCE} – under a maintenance workflow; transitions into and
 *       out of this status are reserved for a future maintenance module and must
 *       not be exposed by the equipment catalog.</li>
 *   <li>{@link #OUT_OF_SERVICE} – taken out of use by an admin or maintenance user.</li>
 *   <li>{@link #RETIRED} – permanently decommissioned; terminal state.</li>
 * </ul>
 */
public enum EquipmentStatus {
    AVAILABLE,
    MAINTENANCE,
    OUT_OF_SERVICE,
    RETIRED
}
