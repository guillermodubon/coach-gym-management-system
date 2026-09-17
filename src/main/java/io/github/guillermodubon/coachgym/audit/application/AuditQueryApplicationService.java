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

/** ADMIN-only, read-only use cases for the audit history query capability. */
@Service
public class AuditQueryApplicationService {

    private final AuditEntryQuery auditEntryQuery;

    public AuditQueryApplicationService(AuditEntryQuery auditEntryQuery) {
        this.auditEntryQuery = Objects.requireNonNull(
                auditEntryQuery,
                "Audit entry query is required.");
    }

    /** Returns a bounded newest-first page for a validated audit query. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public AuditEntryPage findAll(AuditSearchQuery query) {
        return auditEntryQuery.findAll(
                query == null ? AuditSearchQuery.defaults() : query);
    }

    /** Returns sanitized details for one entry or raises a safe not-found error. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public AuditEntryDetails findById(UUID auditEntryId) {
        requireId(auditEntryId);
        return auditEntryQuery.findById(auditEntryId)
                .orElseThrow(() -> new AuditEntryNotFoundException(auditEntryId));
    }

    private static void requireId(UUID auditEntryId) {
        if (auditEntryId == null) {
            throw new AuditQueryValidationException(
                    "Audit entry id must be provided.");
        }
    }
}
