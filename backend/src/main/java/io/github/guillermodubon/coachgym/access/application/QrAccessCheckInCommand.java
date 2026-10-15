package io.github.guillermodubon.coachgym.access.application;

import io.github.guillermodubon.coachgym.access.domain.QrAccessPayloadValidationException;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialQrPayload;

/**
 * Application command for a QR check-in request.
 *
 * <p>Construction is the boundary at which the canonical credential payload
 * is parsed. Later workflow blocks may pass the resulting value to the
 * credential resolver, but this command contains no client, membership,
 * payment, actor, timestamp, or access-decision fields.</p>
 */
public record QrAccessCheckInCommand(AccessCredentialQrPayload payload) {

    public QrAccessCheckInCommand {
        if (payload == null) {
            throw new QrAccessPayloadValidationException();
        }
    }

    /**
     * Parses the approved versioned payload without echoing malformed input.
     *
     * @param rawPayload scanner-provided QR text
     * @return command carrying the canonical payload
     * @throws QrAccessPayloadValidationException when the payload is malformed
     */
    public static QrAccessCheckInCommand parse(String rawPayload) {
        try {
            return new QrAccessCheckInCommand(
                    AccessCredentialQrPayload.parse(rawPayload));
        } catch (IllegalArgumentException exception) {
            throw new QrAccessPayloadValidationException();
        }
    }
}
