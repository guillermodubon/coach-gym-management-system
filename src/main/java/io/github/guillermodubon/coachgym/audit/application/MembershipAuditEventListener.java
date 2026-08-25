package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.membership.*;
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

    @EventListener
    void record(
            MembershipRenewed event) {

        auditEntryStore.recordMembershipRenewed(
                event);
    }

    @EventListener
    void record(
            MembershipFrozen event) {

        auditEntryStore.recordMembershipFrozen(
                event);
    }

    @EventListener
    void record(
            MembershipReactivated event) {

        auditEntryStore.recordMembershipReactivated(
                event);
    }

    @EventListener
    void record(
            MembershipCancelled event) {

        auditEntryStore.recordMembershipCancelled(
                event);
    }
}
