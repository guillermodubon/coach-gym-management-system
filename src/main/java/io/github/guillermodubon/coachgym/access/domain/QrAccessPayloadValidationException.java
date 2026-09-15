package io.github.guillermodubon.coachgym.access.domain;

/**
 * Indicates that a scanned QR payload does not use the approved opaque
 * versioned format.
 *
 * <p>The message is deliberately constant so malformed input is never echoed
 * to logs, error responses, or diagnostics.</p>
 */
public final class QrAccessPayloadValidationException
        extends AccessValidationException {

    /** Creates the safe QR payload validation failure. */
    public QrAccessPayloadValidationException() {
        super("QR access payload is invalid.");
    }
}
