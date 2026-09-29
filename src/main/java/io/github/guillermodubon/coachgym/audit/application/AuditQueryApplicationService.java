package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.audit.AuditEntryDetails;
import io.github.guillermodubon.coachgym.audit.AuditEntryPage;
import io.github.guillermodubon.coachgym.audit.AuditEntryQuery;
import io.github.guillermodubon.coachgym.audit.AuditQueryValidationException;
import io.github.guillermodubon.coachgym.audit.AuditSearchQuery;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only use cases for scope-authorized audit history queries. */
@Service
public class AuditQueryApplicationService {

    private final AuditEntryQuery auditEntryQuery;
    private final AuditQueryAuthorization authorization;

    public AuditQueryApplicationService(
            AuditEntryQuery auditEntryQuery,
            AuditQueryAuthorization authorization) {
        this.auditEntryQuery = Objects.requireNonNull(
                auditEntryQuery,
                "Audit entry query is required.");
        this.authorization = Objects.requireNonNull(
                authorization,
                "Audit query authorization is required.");
    }

    /** Returns a bounded page after resolving branch filters against persisted authority. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public AuditEntryPage findAll(AuditSearchQuery query, UUID actorUserId) {
        AuditSearchQuery validated = query == null
                ? AuditSearchQuery.defaults()
                : query;
        var visibilityScope = authorization.authorizeQuery(
                actorUserId, validated.branchIds());
        return auditEntryQuery.findAll(validated, visibilityScope);
    }

    /** Reauthorizes visibility independently for one detail request. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public AuditEntryDetails findById(UUID auditEntryId, UUID actorUserId) {
        requireId(auditEntryId);
        var visibilityScope = authorization.authorizeDetail(actorUserId);
        return auditEntryQuery.findById(auditEntryId, visibilityScope)
                .orElseThrow(() -> new AuditEntryNotFoundException(auditEntryId));
    }

    private static void requireId(UUID auditEntryId) {
        if (auditEntryId == null) {
            throw new AuditQueryValidationException(
                    "Audit entry id must be provided.");
        }
    }
}
