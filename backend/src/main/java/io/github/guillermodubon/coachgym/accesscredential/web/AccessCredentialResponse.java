package io.github.guillermodubon.coachgym.accesscredential.web;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialDetails;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/** Safe credential metadata; token material, digests and storage details are excluded. */
@Schema(description = "Nonsecret metadata for a client access credential.")
record AccessCredentialResponse(
        UUID id,
        UUID clientId,
        String credentialCode,
        AccessCredentialStatus status,
        String payloadVersion,
        Instant issuedAt,
        UUID issuedByUserId,
        Instant revokedAt,
        UUID revokedByUserId,
        UUID replacedByCredentialId,
        long version,
        URI downloadUrl) {

    static AccessCredentialResponse from(AccessCredentialDetails details, URI downloadUrl) {
        return new AccessCredentialResponse(
                details.id(),
                details.clientId(),
                details.credentialCode(),
                details.status(),
                details.payloadVersion(),
                details.issuedAt(),
                details.issuedByUserId(),
                details.revokedAt(),
                details.revokedByUserId(),
                details.replacedByCredentialId(),
                details.version(),
                downloadUrl);
    }
}
