package io.github.guillermodubon.coachgym.equipment.domain;

/**
 * Enforces the catalog-managed status-transition rules.
 *
 * <p>Allowed transitions:</p>
 * <ul>
 *   <li>{@code AVAILABLE -> OUT_OF_SERVICE}</li>
 *   <li>{@code OUT_OF_SERVICE -> AVAILABLE}</li>
 *   <li>{@code AVAILABLE -> RETIRED}</li>
 *   <li>{@code OUT_OF_SERVICE -> RETIRED}</li>
 * </ul>
 *
 * <p>All other transitions, including any transition involving
 * {@code MAINTENANCE} and any self-transition, are rejected with
 * {@link EquipmentValidationException}.</p>
 *
 * <p>{@code RETIRED} is a terminal status. Therefore, every transition
 * originating from {@code RETIRED} is rejected as terminal, including
 * {@code RETIRED -> RETIRED}.</p>
 *
 * <p>Role-based authorization is enforced in the application layer.
 * This policy validates only the business and structural correctness
 * of a requested transition.</p>
 */
public final class EquipmentStatusPolicy {

    private EquipmentStatusPolicy() {
    }

    /**
     * Validates a catalog-managed equipment status transition.
     *
     * @param current current equipment status
     * @param target desired target status
     * @param reason non-blank reason for the transition
     * @return validated and normalized transition
     * @throws EquipmentValidationException when the transition is invalid
     */
    public static EquipmentStatusTransition validate(
            EquipmentStatus current,
            EquipmentStatus target,
            String reason) {

        validateRequiredStatuses(
                current,
                target);

        /*
         * RETIRED has precedence over the same-status rule and the
         * MAINTENANCE rule. Every transition originating from RETIRED
         * must be reported as a terminal-state conflict.
         */
        if (current == EquipmentStatus.RETIRED) {
            throw new EquipmentValidationException(
                    "RETIRED is a terminal status; "
                            + "no further transitions are allowed.");
        }

        if (current == target) {
            throw new EquipmentValidationException(
                    "Equipment is already in status "
                            + current.name()
                            + ".");
        }

        if (current == EquipmentStatus.MAINTENANCE
                || target == EquipmentStatus.MAINTENANCE) {

            throw new EquipmentValidationException(
                    "Transitions involving MAINTENANCE are reserved "
                            + "for the maintenance workflow.");
        }

        if (!isAllowed(current, target)) {
            throw new EquipmentValidationException(
                    "Transition from "
                            + current.name()
                            + " to "
                            + target.name()
                            + " is not permitted.");
        }

        return new EquipmentStatusTransition(
                current,
                target,
                reason);
    }

    /**
     * Validates that both status values are present.
     */
    private static void validateRequiredStatuses(
            EquipmentStatus current,
            EquipmentStatus target) {

        if (current == null) {
            throw new EquipmentValidationException(
                    "Current equipment status must not be null.");
        }

        if (target == null) {
            throw new EquipmentValidationException(
                    "Target equipment status must not be null.");
        }
    }

    /**
     * Determines whether the requested transition belongs to the set of
     * transitions managed directly by the equipment catalog.
     */
    private static boolean isAllowed(
            EquipmentStatus from,
            EquipmentStatus to) {

        return isAllowedFromAvailable(from, to)
                || isAllowedFromOutOfService(from, to);
    }

    /**
     * Determines whether a transition originating from AVAILABLE is allowed.
     */
    private static boolean isAllowedFromAvailable(
            EquipmentStatus from,
            EquipmentStatus to) {

        return from == EquipmentStatus.AVAILABLE
                && (to == EquipmentStatus.OUT_OF_SERVICE
                || to == EquipmentStatus.RETIRED);
    }

    /**
     * Determines whether a transition originating from OUT_OF_SERVICE
     * is allowed.
     */
    private static boolean isAllowedFromOutOfService(
            EquipmentStatus from,
            EquipmentStatus to) {

        return from == EquipmentStatus.OUT_OF_SERVICE
                && (to == EquipmentStatus.AVAILABLE
                || to == EquipmentStatus.RETIRED);
    }
}
