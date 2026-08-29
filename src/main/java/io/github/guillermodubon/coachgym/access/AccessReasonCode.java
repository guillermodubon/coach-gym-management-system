package io.github.guillermodubon.coachgym.access;

/**
 * Typed reason for an access attempt result.
 *
 * <p>Consistency rule enforced by the schema (V14):</p>
 * <ul>
 *   <li>{@code AccessResult.ALLOWED} is always paired with
 *       {@code ACCESS_ALLOWED}.</li>
 *   <li>{@code AccessResult.DENIED} is always paired with one of the
 *       eight denial codes.</li>
 * </ul>
 *
 * <p>Denial precedence (highest to lowest) matches {@code AccessPolicy}:</p>
 * <ol>
 *   <li>{@code IDENTIFIER_NOT_FOUND}</li>
 *   <li>{@code CLIENT_INACTIVE}</li>
 *   <li>{@code MEMBERSHIP_NOT_FOUND}</li>
 *   <li>{@code MEMBERSHIP_CANCELLED}</li>
 *   <li>{@code MEMBERSHIP_FROZEN}</li>
 *   <li>{@code MEMBERSHIP_EXPIRED}</li>
 *   <li>{@code MEMBERSHIP_PERIOD_EXPIRED}</li>
 *   <li>{@code MEMBERSHIP_NOT_STARTED}</li>
 * </ol>
 */
public enum AccessReasonCode {

    // ── Allowed ───────────────────────────────────────────────────────────────

    /** All checks passed; access is granted. */
    ACCESS_ALLOWED,

    // ── Denied ────────────────────────────────────────────────────────────────

    /**
     * The presented identifier could not be resolved to a known client
     * or membership.
     */
    IDENTIFIER_NOT_FOUND,

    /** The resolved client account is inactive. */
    CLIENT_INACTIVE,

    /**
     * No current (ACTIVE or FROZEN) membership was found for the resolved
     * client.
     */
    MEMBERSHIP_NOT_FOUND,

    /**
     * The resolved membership's current period has not yet started on the
     * operational date.
     */
    MEMBERSHIP_NOT_STARTED,

    /**
     * The resolved membership's current period effective end date is in
     * the past relative to the operational date.
     */
    MEMBERSHIP_PERIOD_EXPIRED,

    /**
     * The resolved membership is currently frozen and the operational date
     * falls within the open freeze window.
     */
    MEMBERSHIP_FROZEN,

    /** The resolved membership status is EXPIRED. */
    MEMBERSHIP_EXPIRED,

    /** The resolved membership status is CANCELLED. */
    MEMBERSHIP_CANCELLED
}
