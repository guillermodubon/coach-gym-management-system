package io.github.guillermodubon.coachgym.access.application;

import io.github.guillermodubon.coachgym.access.AccessReasonCode;
import io.github.guillermodubon.coachgym.access.AccessResult;
import io.github.guillermodubon.coachgym.access.domain.AccessIdentifierType;
import java.time.Instant;
import java.util.UUID;

/**
 * Safe, non-persistent result of evaluating a resolved QR credential.
 *
 * <p>Block 3 deliberately stops before access-attempt persistence. This
 * contract carries the same policy outcome and operational snapshots needed by
 * the later transactional workflow, while retaining the resolved credential
 * relation without carrying the QR payload, token, fingerprint, or provider
 * types.</p>
 */
public record QrAccessCheckInResult(
        UUID credentialId,
        AccessIdentifierType identificationSource,
        UUID clientId,
        String clientCode,
        UUID membershipId,
        String membershipCode,
        UUID membershipPeriodId,
        AccessResult result,
        AccessReasonCode reasonCode,
        String reason,
        Instant evaluatedAt) {

    public QrAccessCheckInResult {
        if (credentialId == null) {
            throw new IllegalArgumentException(
                    "QR credential identifier must be provided.");
        }
        if (identificationSource != AccessIdentifierType.QR_CREDENTIAL) {
            throw new IllegalArgumentException(
                    "QR results must use QR_CREDENTIAL as their source.");
        }
        if (clientId == null) {
            throw new IllegalArgumentException(
                    "Resolved client identifier must be provided.");
        }
        if (result == null) {
            throw new IllegalArgumentException(
                    "Access result must be provided.");
        }
        if (reasonCode == null) {
            throw new IllegalArgumentException(
                    "Access reason code must be provided.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "Access decision reason must be provided.");
        }
        if (evaluatedAt == null) {
            throw new IllegalArgumentException(
                    "QR evaluation timestamp must be provided.");
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

        clientCode = normalizeNullable(clientCode);
        membershipCode = normalizeNullable(membershipCode);
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}
