package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.membership.MembershipCreated;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class MembershipAuditEventListener {

    private final AuditEntryStore auditEntryStore;

    MembershipAuditEventListener(
            AuditEntryStore auditEntryStore) {

        this.auditEntryStore =
                auditEntryStore;
    }

    @EventListener
    void record(
            MembershipCreated event) {

        auditEntryStore.recordMembershipCreated(
                event);
    }
}
