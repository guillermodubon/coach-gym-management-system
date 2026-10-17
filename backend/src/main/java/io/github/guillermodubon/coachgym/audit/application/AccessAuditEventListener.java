package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.access.AccessAttemptRecorded;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Creates security-relevant audit entries for denied gym access attempts.
 *
 * <p>Allowed attempts remain available in the authoritative access-record
 * history and are intentionally not duplicated in the audit table.</p>
 */
@Component
public class AccessAuditEventListener {

    private final AuditEntryStore auditEntryStore;

    public AccessAuditEventListener(
            AuditEntryStore auditEntryStore) {
        this.auditEntryStore = auditEntryStore;
    }

    @EventListener
    public void record(AccessAttemptRecorded event) {
        if (event.denied()) {
            auditEntryStore.recordDeniedAccessAttempt(event);
        }
    }
}
