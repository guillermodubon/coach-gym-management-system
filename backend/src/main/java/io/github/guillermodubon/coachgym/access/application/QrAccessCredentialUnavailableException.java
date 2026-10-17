package io.github.guillermodubon.coachgym.access.application;

/**
 * Signals that a scanned QR credential cannot be used for access resolution.
 *
 * <p>The message is intentionally constant. Unknown, revoked, replaced, and
 * otherwise inactive credentials must not be distinguishable to callers, and
 * no payload or protected lookup value is ever included.</p>
 */
public final class QrAccessCredentialUnavailableException
        extends RuntimeException {

    public QrAccessCredentialUnavailableException() {
        super("QR access credential is unavailable.");
    }
}
