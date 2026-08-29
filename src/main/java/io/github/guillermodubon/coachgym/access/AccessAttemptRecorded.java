package io.github.guillermodubon.coachgym.access;

import java.time.Instant;
import java.util.UUID;

/**
 * Published after an access attempt has been persisted successfully.
 *
 * <p>The event is published for both allowed and denied attempts. Consumers
 * decide independently whether a particular result requires an additional
 * side effect.</p>
 *
 * <p>The event deliberately excludes personal contact information,
 * authentication credentials, cookies, tokens and session identifiers.</p>
 */
public record AccessAttemptRecorded(
        UUID accessRecordId,
        String presentedIdentifier,
        String presentedIdentifierType,
        UUID clientId,
        String clientCode,
        UUID membershipId,
        String membershipCode,
        AccessResult result,
        AccessReasonCode reasonCode,
        Instant checkedInAt,
        UUID actorUserId,
        String actorIdentifier,
        Instant occurredAt) {

    public AccessAttemptRecorded {
        if (accessRecordId == null) {
            throw new IllegalArgumentException(
                    "Access record identifier must be provided.");
        }

        if (presentedIdentifier == null
                || presentedIdentifier.isBlank()) {
            throw new IllegalArgumentException(
                    "Presented identifier must be provided.");
        }

        if (presentedIdentifierType == null
                || presentedIdentifierType.isBlank()) {
            throw new IllegalArgumentException(
                    "Presented identifier type must be provided.");
        }

        if (result == null) {
            throw new IllegalArgumentException(
                    "Access result must be provided.");
        }

        if (reasonCode == null) {
            throw new IllegalArgumentException(
                    "Access reason code must be provided.");
        }

        if (checkedInAt == null) {
            throw new IllegalArgumentException(
                    "Access check-in timestamp must be provided.");
        }

        if (actorUserId == null) {
            throw new IllegalArgumentException(
                    "Access actor identifier must be provided.");
        }

        if (actorIdentifier == null
                || actorIdentifier.isBlank()) {
            throw new IllegalArgumentException(
                    "Access actor snapshot must be provided.");
        }

        if (occurredAt == null) {
            throw new IllegalArgumentException(
                    "Event occurrence timestamp must be provided.");
        }

        if (result == AccessResult.ALLOWED
                && reasonCode != AccessReasonCode.ACCESS_ALLOWED) {
            throw new IllegalArgumentException(
                    "Allowed access must use ACCESS_ALLOWED.");
        }

        if (result == AccessResult.DENIED
                && reasonCode == AccessReasonCode.ACCESS_ALLOWED) {
            throw new IllegalArgumentException(
                    "Denied access must use a denial reason code.");
        }

        presentedIdentifier = presentedIdentifier.trim();
        presentedIdentifierType =
                presentedIdentifierType.trim();
        clientCode = normalizeNullable(clientCode);
        membershipCode = normalizeNullable(membershipCode);
        actorIdentifier = actorIdentifier.trim();
    }

    public boolean denied() {
        return result == AccessResult.DENIED;
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}
