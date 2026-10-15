package io.github.guillermodubon.coachgym.accesscredential.web;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialHistoryDetails;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Safe lifecycle-history entry; it contains no token or artifact material. */
@Schema(description = "Immutable access-credential lifecycle history entry.")
record AccessCredentialHistoryResponse(
        UUID id,
        UUID credentialId,
        UUID clientId,
        AccessCredentialStatus previousStatus,
        AccessCredentialStatus newStatus,
        String reason,
        Instant occurredAt,
        UUID changedByUserId,
        UUID replacementCredentialId) {

    static AccessCredentialHistoryResponse from(AccessCredentialHistoryDetails details) {
        return new AccessCredentialHistoryResponse(
                details.id(),
                details.credentialId(),
                details.clientId(),
                details.previousStatus(),
                details.newStatus(),
                details.reason(),
                details.occurredAt(),
                details.changedByUserId(),
                details.replacementCredentialId());
    }
}
