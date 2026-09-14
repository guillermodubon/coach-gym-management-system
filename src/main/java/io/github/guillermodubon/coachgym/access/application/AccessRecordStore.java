package io.github.guillermodubon.coachgym.access.application;

import io.github.guillermodubon.coachgym.access.AccessRecordDetails;
import io.github.guillermodubon.coachgym.access.AccessReasonCode;
import io.github.guillermodubon.coachgym.access.AccessResult;
import java.time.Instant;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Port for persisting and querying access records.
 *
 * <p>The JPA adapter is the authoritative PostgreSQL implementation. All
 * nullable parameters mirror the nullable FK columns in
 * {@code gym.access_records}.</p>
 */
public interface AccessRecordStore {

    /**
     * Persists one append-only access record and returns the stored projection.
     *
     * @param presentedIdentifier the normalised identifier as entered
     * @param clientId            nullable; null when identifier was not resolved
     * @param clientCode          nullable; null when identifier was not resolved
     * @param membershipId        nullable; null when no membership was found
     * @param membershipCode      nullable; null when no membership was found
     * @param membershipPeriodId  nullable; null when no membership was found
     * @param result              ALLOWED or DENIED
     * @param reasonCode          one of the nine approved codes
     * @param reason              human-readable reason text
     * @param occurredAt          the single captured instant
     * @param actorId             the authenticated processing user's identifier;
     *                            required when inserting a record, although the database
     *                            may later set the value to null if the user is deleted
     */
    AccessRecordDetails persist(
            String presentedIdentifier,
            UUID clientId,
            String clientCode,
            UUID membershipId,
            String membershipCode,
            UUID membershipPeriodId,
            AccessResult result,
            AccessReasonCode reasonCode,
            String reason,
            Instant occurredAt,
            UUID actorId);

    /**
     * Persists one QR access attempt with its safe source and credential
     * relation. The payload marker is never the opaque QR token.
     */
    AccessRecordDetails persistQr(
            String safeIdentifier,
            UUID credentialId,
            UUID clientId,
            String clientCode,
            UUID membershipId,
            String membershipCode,
            UUID membershipPeriodId,
            AccessResult result,
            AccessReasonCode reasonCode,
            String reason,
            Instant occurredAt,
            UUID actorId);

    /**
     * Returns the most recent successful QR attempt for one credential from
     * the supplied inclusive server-time boundary.
     */
    Optional<AccessRecordDetails> findMostRecentAllowedQrAttempt(
            UUID credentialId,
            Instant occurredAtFromInclusive);

    Optional<AccessRecordDetails> findById(UUID id);

    AccessRecordPage findAll(AccessRecordSearchQuery query);
}
