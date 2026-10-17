package io.github.guillermodubon.coachgym.access;

/**
 * The outcome of a gym access attempt.
 *
 * <p>Both {@code ALLOWED} and {@code DENIED} are normal business results.
 * Both return HTTP 200. Neither is an error condition.</p>
 */
public enum AccessResult {

    /** The check-in attempt was approved. */
    ALLOWED,

    /** The check-in attempt was rejected for a business reason. */
    DENIED
}
