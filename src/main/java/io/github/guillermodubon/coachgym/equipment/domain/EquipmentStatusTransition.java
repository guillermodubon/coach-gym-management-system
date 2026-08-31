package io.github.guillermodubon.coachgym.equipment.domain;

/**
 * Value object representing a validated, catalog-managed status transition.
 *
 * <p>Instances are produced exclusively by {@link EquipmentStatusPolicy}. Callers
 * must not construct this record directly; use the policy to obtain a validated
 * transition.
 *
 * @param from   the previous status (non-null)
 * @param to     the target status (non-null)
 * @param reason a normalized, non-blank reason supplied by the actor
 */
public record EquipmentStatusTransition(
        EquipmentStatus from,
        EquipmentStatus to,
        String reason) {

    private static final int MAX_REASON_LENGTH = 2_000;

    /** Package-private constructor called only by {@link EquipmentStatusPolicy}. */
    public EquipmentStatusTransition {
        if (from == null) {
            throw new EquipmentValidationException("Transition source status must not be null.");
        }
        if (to == null) {
            throw new EquipmentValidationException("Transition target status must not be null.");
        }
        if (reason == null || reason.isBlank()) {
            throw new EquipmentValidationException("Transition reason must not be blank.");
        }
        reason = reason.trim();
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new EquipmentValidationException(
                    "Transition reason must not exceed " + MAX_REASON_LENGTH + " characters.");
        }
    }
}
