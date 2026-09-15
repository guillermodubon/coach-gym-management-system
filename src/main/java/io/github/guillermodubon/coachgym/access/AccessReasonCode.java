package io.github.guillermodubon.coachgym.access;

/**
 * Typed reason for an access attempt result.
 *
 * <p>The deployed access schema enforces both manual and QR check-in codes.
 * QR-specific codes are persisted only through the transactional QR workflow
 * and do not alter manual policy precedence.</p>
 * <ul>
 *   <li>{@code AccessResult.ALLOWED} is always paired with
 *       {@code ACCESS_ALLOWED}.</li>
 *   <li>{@code AccessResult.DENIED} is always paired with a denial code.</li>
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
 *
 * <p>QR workflow-specific denials are {@code ACCESS_CREDENTIAL_INVALID} and
 * {@code DUPLICATE_CHECK_IN}; they do not alter the manual policy precedence.</p>
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
    MEMBERSHIP_CANCELLED,

    /** The supplied QR credential is unknown or no longer active. */
    ACCESS_CREDENTIAL_INVALID,

    /** The same credential or resolved client was scanned too recently. */
    DUPLICATE_CHECK_IN
}
