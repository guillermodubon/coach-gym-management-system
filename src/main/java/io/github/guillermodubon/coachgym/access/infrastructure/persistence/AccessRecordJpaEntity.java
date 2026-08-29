package io.github.guillermodubon.coachgym.access.infrastructure.persistence;

import io.github.guillermodubon.coachgym.access.AccessReasonCode;
import io.github.guillermodubon.coachgym.access.AccessRecordDetails;
import io.github.guillermodubon.coachgym.access.AccessResult;
import io.github.guillermodubon.coachgym.access.domain.AccessValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "gym", name = "access_records")
class AccessRecordJpaEntity {

    @Id
    private UUID id;

    @Column(
            name = "entered_code",
            nullable = false,
            length = 64,
            updatable = false)
    private String presentedIdentifier;

    @Column(
            name = "client_id",
            updatable = false)
    private UUID clientId;

    @Column(
            name = "client_code_snapshot",
            length = 32,
            updatable = false)
    private String clientCode;

    @Column(
            name = "membership_id",
            updatable = false)
    private UUID membershipId;

    @Column(
            name = "membership_code_snapshot",
            length = 32,
            updatable = false)
    private String membershipCode;

    @Column(
            name = "membership_period_id",
            updatable = false)
    private UUID membershipPeriodId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "decision",
            nullable = false,
            length = 10,
            updatable = false)
    private AccessResult result;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "reason_code",
            nullable = false,
            length = 40,
            updatable = false)
    private AccessReasonCode reasonCode;

    @Column(
            name = "details",
            columnDefinition = "text",
            updatable = false)
    private String reason;

    @Column(
            name = "occurred_at",
            nullable = false,
            updatable = false)
    private Instant checkedInAt;

    @Column(
            name = "recorded_by_user_id",
            updatable = false)
    private UUID processedByUserId;

    protected AccessRecordJpaEntity() {
    }

    static AccessRecordJpaEntity create(
            String presentedIdentifier,
            UUID clientId,
            String clientCode,
            UUID membershipId,
            String membershipCode,
            UUID membershipPeriodId,
            AccessResult result,
            AccessReasonCode reasonCode,
            String reason,
            Instant checkedInAt,
            UUID processedByUserId) {

        validateRequiredValues(
                presentedIdentifier,
                result,
                reasonCode,
                reason,
                checkedInAt,
                processedByUserId);

        validateResolutionConsistency(
                clientId,
                clientCode,
                membershipId,
                membershipCode,
                membershipPeriodId);

        validateResultConsistency(result, reasonCode);

        AccessRecordJpaEntity entity = new AccessRecordJpaEntity();
        entity.id = UUID.randomUUID();
        entity.presentedIdentifier = presentedIdentifier.trim();
        entity.clientId = clientId;
        entity.clientCode = normalizeNullable(clientCode);
        entity.membershipId = membershipId;
        entity.membershipCode = normalizeNullable(membershipCode);
        entity.membershipPeriodId = membershipPeriodId;
        entity.result = result;
        entity.reasonCode = reasonCode;
        entity.reason = reason.trim();
        entity.checkedInAt = checkedInAt;
        entity.processedByUserId = processedByUserId;

        return entity;
    }

    AccessRecordDetails toDetails() {
        return new AccessRecordDetails(
                id,
                presentedIdentifier,
                clientId,
                clientCode,
                membershipId,
                membershipCode,
                result,
                reasonCode,
                reason,
                checkedInAt,
                processedByUserId);
    }

    UUID id() {
        return id;
    }

    UUID membershipPeriodId() {
        return membershipPeriodId;
    }

    private static void validateRequiredValues(
            String presentedIdentifier,
            AccessResult result,
            AccessReasonCode reasonCode,
            String reason,
            Instant checkedInAt,
            UUID processedByUserId) {

        if (presentedIdentifier == null
                || presentedIdentifier.isBlank()) {
            throw new AccessValidationException(
                    "Presented access identifier must be provided.");
        }

        if (presentedIdentifier.trim().length() > 64) {
            throw new AccessValidationException(
                    "Presented access identifier must not exceed 64 characters.");
        }

        if (result == null) {
            throw new AccessValidationException(
                    "Access result must be provided.");
        }

        if (reasonCode == null) {
            throw new AccessValidationException(
                    "Access reason code must be provided.");
        }

        if (reason == null || reason.isBlank()) {
            throw new AccessValidationException(
                    "Access reason text must be provided.");
        }

        if (checkedInAt == null) {
            throw new AccessValidationException(
                    "Access check-in timestamp must be provided.");
        }

        if (processedByUserId == null) {
            throw new AccessValidationException(
                    "Access processing user must be provided.");
        }
    }

    private static void validateResolutionConsistency(
            UUID clientId,
            String clientCode,
            UUID membershipId,
            String membershipCode,
            UUID membershipPeriodId) {

        if ((clientId == null) != isBlank(clientCode)) {
            throw new AccessValidationException(
                    "Client identifier and client code must be present together.");
        }

        boolean membershipCompletelyAbsent =
                membershipId == null
                        && isBlank(membershipCode)
                        && membershipPeriodId == null;

        boolean membershipCompletelyPresent =
                membershipId != null
                        && !isBlank(membershipCode)
                        && membershipPeriodId != null
                        && clientId != null;

        if (!membershipCompletelyAbsent
                && !membershipCompletelyPresent) {
            throw new AccessValidationException(
                    "Membership identifier, code and period must be present together.");
        }
    }

    private static void validateResultConsistency(
            AccessResult result,
            AccessReasonCode reasonCode) {

        if (result == AccessResult.ALLOWED
                && reasonCode != AccessReasonCode.ACCESS_ALLOWED) {
            throw new AccessValidationException(
                    "Allowed access must use ACCESS_ALLOWED.");
        }

        if (result == AccessResult.DENIED
                && reasonCode == AccessReasonCode.ACCESS_ALLOWED) {
            throw new AccessValidationException(
                    "Denied access must use a denial reason code.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalizeNullable(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
