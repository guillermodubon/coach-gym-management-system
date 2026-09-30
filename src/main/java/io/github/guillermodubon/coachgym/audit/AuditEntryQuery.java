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

    /** Returns one bounded summary page after applying the resolved visibility scope. */
    AuditEntryPage findAll(AuditSearchQuery query, AuditVisibilityScope visibilityScope);

    /** Returns safe details only when visible within the independently resolved scope. */
    Optional<AuditEntryDetails> findById(
            UUID auditEntryId, AuditVisibilityScope visibilityScope);

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
            AuditVisibilityScope visibilityScope,
            AuditExportSink sink);

    /** Alias used by application services that call list operations "search". */
    default AuditEntryPage search(
            AuditSearchQuery query, AuditVisibilityScope visibilityScope) {
        return findAll(query, visibilityScope);
    }

    /** Alias matching other module query ports. */
    default AuditEntryPage findPage(
            AuditSearchQuery query, AuditVisibilityScope visibilityScope) {
        return findAll(query, visibilityScope);
    }
}
