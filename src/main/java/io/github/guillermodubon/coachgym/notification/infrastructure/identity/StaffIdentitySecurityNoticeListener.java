package io.github.guillermodubon.coachgym.notification.infrastructure.identity;

import io.github.guillermodubon.coachgym.shared.identityemail.IdentityEmailDeliveryStatus;
import io.github.guillermodubon.coachgym.shared.identityemail.IdentitySecurityNoticeType;
import io.github.guillermodubon.coachgym.shared.identityemail.StaffIdentitySecurityEmail;
import io.github.guillermodubon.coachgym.shared.identityemail.StaffIdentitySecurityEmailSender;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffBranchAssigned;
import io.github.guillermodubon.coachgym.user.StaffBranchAssignmentEnded;
import io.github.guillermodubon.coachgym.user.StaffIdentityLifecycleChanged;
import io.github.guillermodubon.coachgym.user.StaffIdentityNoticeQuery;
import io.github.guillermodubon.coachgym.user.StaffIdentityNoticeRecipient;
import io.github.guillermodubon.coachgym.user.StaffIdentityStatus;
import io.github.guillermodubon.coachgym.user.StaffPasswordReset;
import io.github.guillermodubon.coachgym.user.StaffRoleScopeChanged;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Isolates best-effort identity security email delivery from committed account changes. */
@Component
class StaffIdentitySecurityNoticeListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(StaffIdentitySecurityNoticeListener.class);

    private final StaffIdentityNoticeQuery recipients;
    private final StaffIdentitySecurityEmailSender sender;

    StaffIdentitySecurityNoticeListener(
            StaffIdentityNoticeQuery recipients,
            StaffIdentitySecurityEmailSender sender) {
        this.recipients = recipients;
        this.sender = sender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onLifecycleChanged(StaffIdentityLifecycleChanged event) {
        IdentitySecurityNoticeType type = switch (event.newStatus()) {
            case SUSPENDED -> IdentitySecurityNoticeType.ACCOUNT_SUSPENDED;
            case ACTIVE -> IdentitySecurityNoticeType.ACCOUNT_REACTIVATED;
            case DEACTIVATED -> IdentitySecurityNoticeType.ACCOUNT_DEACTIVATED;
            case INVITED -> null;
        };
        if (type != null) {
            deliver(event.targetUserId(), type, "", "", event.occurredAt());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onRoleScopeChanged(StaffRoleScopeChanged event) {
        String role = event.newRoles().stream()
                .map(RoleCode::name)
                .sorted()
                .findFirst()
                .orElse("STAFF");
        deliver(event.targetUserId(), IdentitySecurityNoticeType.AUTHORITY_CHANGED,
                role, event.newScope().name(), event.occurredAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onPasswordReset(StaffPasswordReset event) {
        deliver(event.userId(), IdentitySecurityNoticeType.PASSWORD_CHANGED,
                "", "", event.occurredAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onBranchAssigned(StaffBranchAssigned event) {
        deliver(event.targetUserId(), IdentitySecurityNoticeType.BRANCH_ASSIGNMENTS_CHANGED,
                "", "", event.occurredAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onBranchAssignmentEnded(StaffBranchAssignmentEnded event) {
        deliver(event.targetUserId(), IdentitySecurityNoticeType.BRANCH_ASSIGNMENTS_CHANGED,
                "", "", event.occurredAt());
    }

    private void deliver(
            UUID userId,
            IdentitySecurityNoticeType type,
            String role,
            String scope,
            java.time.Instant occurredAt) {
        try {
            Optional<StaffIdentityNoticeRecipient> recipient = recipients.findNoticeRecipient(userId);
            if (recipient.isEmpty()) {
                return;
            }
            StaffIdentityNoticeRecipient target = recipient.get();
            IdentityEmailDeliveryStatus status = sender.sendSecurityNotice(new StaffIdentitySecurityEmail(
                    target.email(), target.displayName(), type, role, scope, occurredAt));
            if (status != IdentityEmailDeliveryStatus.SENT) {
                LOGGER.warn(
                        "Staff security notice delivery was not confirmed "
                                + "(event=staff_security_notice, type={}, outcome={}).",
                        type.name(), status == null ? "UNKNOWN" : status.name());
            }
        } catch (RuntimeException ignored) {
            LOGGER.warn(
                    "Staff security notice delivery failed "
                            + "(event=staff_security_notice, type={}).",
                    type.name());
        }
    }
}
