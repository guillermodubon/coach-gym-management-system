package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialIssued;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialReplaced;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialRevoked;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists privacy-safe audit entries for access-credential lifecycle events.
 *
 * <p>Credential lifecycle events are published after the canonical transaction
 * commits. Each audit write therefore uses an independent transaction so an
 * audit failure cannot alter the already committed credential state.</p>
 */
@Component
class AccessCredentialAuditEventListener {

    private final AuditEntryStore auditEntryStore;

    AccessCredentialAuditEventListener(AuditEntryStore auditEntryStore) {
        this.auditEntryStore = auditEntryStore;
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void record(AccessCredentialIssued event) {
        auditEntryStore.recordAccessCredentialIssued(event);
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void record(AccessCredentialRevoked event) {
        auditEntryStore.recordAccessCredentialRevoked(event);
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void record(AccessCredentialReplaced event) {
        auditEntryStore.recordAccessCredentialReplaced(event);
    }
}
