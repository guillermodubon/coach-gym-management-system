package io.github.guillermodubon.coachgym.access;

/**
 * Typed reason for an access attempt result.
 *
 * <p>The access schema constrains persisted reason codes. The branch-coverage
 * code is a contract for a later integration and must not be emitted until a
 * Flyway migration adds it to that constraint. QR-specific codes are reserved
 * for the transactional QR workflow and do not alter manual policy
 * precedence.</p>
 * <ul>
 *   <li>{@code AccessResult.ALLOWED} is always paired with
 *       {@code ACCESS_ALLOWED}.</li>
 *   <li>{@code AccessResult.DENIED} is always paired with a denial code.</li>
 * </ul>
 *
 * <p>The manual membership policy order (highest to lowest) remains unchanged:
 * unresolved identifier, inactive client, missing membership, cancelled,
 * frozen, expired, period expired, period not started, branch coverage, then
 * payment requirement.</p>
 * <ol>
 *   <li>{@code IDENTIFIER_NOT_FOUND}</li>
 *   <li>{@code CLIENT_INACTIVE}</li>
 *   <li>{@code MEMBERSHIP_NOT_FOUND}</li>
 *   <li>{@code MEMBERSHIP_CANCELLED}</li>
 *   <li>{@code MEMBERSHIP_FROZEN}</li>
 *   <li>{@code MEMBERSHIP_EXPIRED}</li>
 *   <li>{@code MEMBERSHIP_PERIOD_EXPIRED}</li>
 *   <li>{@code MEMBERSHIP_NOT_STARTED}</li>
 *   <li>{@code MEMBERSHIP_NOT_VALID_AT_BRANCH}</li>
 *   <li>{@code PAYMENT_REQUIRED} (when the optional payment policy is enabled)</li>
 * </ol>

 * <p>When independent QR and membership rules produce competing denials,
 * {@code io.github.guillermodubon.coachgym.access.domain.AccessDenialPrecedence}
 * selects the persisted result. It gives invalid QR credentials and duplicate
 * check-ins precedence over the manual membership order; an empty denial set
 * results in {@code ACCESS_ALLOWED}.</p>
 *
 * <p>Staff authorization, active-branch context, and physical-branch
 * lifecycle failures are preconditions and do not expose membership coverage
 * or produce membership denial details.</p>
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

    /**
     * The active membership period does not include the physical branch.
     * Persist only after the schema constraint is migrated to allow this code.
     */
    MEMBERSHIP_NOT_VALID_AT_BRANCH,

    /** An otherwise eligible period has no qualifying confirmed payment. */
    PAYMENT_REQUIRED,

    /** The supplied QR credential is unknown or no longer active. */
    ACCESS_CREDENTIAL_INVALID,

    /** The same credential or resolved client was scanned too recently. */
    DUPLICATE_CHECK_IN
}
