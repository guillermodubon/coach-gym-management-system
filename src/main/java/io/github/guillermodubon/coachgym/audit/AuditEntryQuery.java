package io.github.guillermodubon.coachgym.audit;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only application port for the ADMIN audit history capability.
 *
 * <p>The interface intentionally exposes no JPA, Spring Data, JDBC,
 * persistence, JSON, or provider types. Implementations remain internal and
 * must apply the policy before touching the database.</p>
 */
public interface AuditEntryQuery {

    /** Returns one bounded summary page for a validated query. */
    AuditEntryPage findAll(AuditSearchQuery query);

    /** Returns safe details for an entry, or empty when its UUID is absent. */
    Optional<AuditEntryDetails> findById(UUID auditEntryId);

    /**
     * Streams bounded, already-sanitized export rows to the supplied sink.
     *
     * <p>The implementation owns the database resources and must not collect
     * the export into an in-memory list. Authorization and HTTP concerns stay
     * in the application/web layers.</p>
     */
    void streamExport(
            AuditExportQuery query,
            AuditExportPolicy policy,
            AuditExportSink sink);

    /** Alias used by application services that call list operations "search". */
    default AuditEntryPage search(AuditSearchQuery query) {
        return findAll(query);
    }

    /** Alias matching other module query ports. */
    default AuditEntryPage findPage(AuditSearchQuery query) {
        return findAll(query);
    }
}
