package io.github.guillermodubon.coachgym.notification.infrastructure.identity;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import io.github.guillermodubon.coachgym.user.StaffRoleScopeChanged;
import io.github.guillermodubon.coachgym.user.StaffScopeType;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class StaffIdentitySecurityNoticeListenerTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000009401");
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000009402");
    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");

    @Mock private StaffIdentityNoticeQuery recipients;
    @Mock private StaffIdentitySecurityEmailSender sender;

    @Test
    void lifecycleNoticeIsBestEffortAndDoesNotCarryReasonOrCredentials() {
        when(recipients.findNoticeRecipient(USER_ID)).thenReturn(Optional.of(
                new StaffIdentityNoticeRecipient("staff@example.test", "Staff Person")));
        when(sender.sendSecurityNotice(org.mockito.ArgumentMatchers.any()))
                .thenReturn(IdentityEmailDeliveryStatus.FAILED);
        StaffIdentitySecurityNoticeListener listener = new StaffIdentitySecurityNoticeListener(
                recipients, sender);
        StaffIdentityLifecycleChanged event = new StaffIdentityLifecycleChanged(
                USER_ID, StaffIdentityStatus.ACTIVE, StaffIdentityStatus.SUSPENDED,
                ACTOR_ID, NOW, true);

        assertThatCode(() -> listener.onLifecycleChanged(event)).doesNotThrowAnyException();

        ArgumentCaptor<StaffIdentitySecurityEmail> notice =
                ArgumentCaptor.forClass(StaffIdentitySecurityEmail.class);
        verify(sender).sendSecurityNotice(notice.capture());
        org.assertj.core.api.Assertions.assertThat(notice.getValue().noticeType())
                .isEqualTo(IdentitySecurityNoticeType.ACCOUNT_SUSPENDED);
        org.assertj.core.api.Assertions.assertThat(notice.getValue().toString())
                .doesNotContain("staff@example.test", "reason", "password");
    }

    @Test
    void transportExceptionCannotRollBackCommittedAuthorityChange(CapturedOutput output) {
        when(recipients.findNoticeRecipient(USER_ID)).thenReturn(Optional.of(
                new StaffIdentityNoticeRecipient("staff@example.test", "Staff Person")));
        when(sender.sendSecurityNotice(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("private transport detail"));
        StaffIdentitySecurityNoticeListener listener = new StaffIdentitySecurityNoticeListener(
                recipients, sender);
        StaffRoleScopeChanged event = new StaffRoleScopeChanged(
                USER_ID, Set.of(RoleCode.RECEPTIONIST), Set.of(RoleCode.ADMIN),
                StaffScopeType.BRANCH, StaffScopeType.BRANCH, ACTOR_ID, NOW, true);

        assertThatCode(() -> listener.onRoleScopeChanged(event)).doesNotThrowAnyException();

        verify(sender).sendSecurityNotice(org.mockito.ArgumentMatchers.any());
        verify(recipients, never()).findNoticeRecipient(ACTOR_ID);
        org.assertj.core.api.Assertions.assertThat(output.getAll())
                .contains("event=staff_security_notice", "type=AUTHORITY_CHANGED")
                .doesNotContain(USER_ID.toString(), ACTOR_ID.toString(),
                        "staff@example.test", "private transport detail");
    }

    @Test
    void assignmentEventsNotifyOnlyTheAffectedStaffMemberWithAMinimizedNotice() {
        when(recipients.findNoticeRecipient(USER_ID)).thenReturn(Optional.of(
                new StaffIdentityNoticeRecipient("staff@example.test", "Staff Person")));
        when(sender.sendSecurityNotice(org.mockito.ArgumentMatchers.any()))
                .thenReturn(IdentityEmailDeliveryStatus.SENT);
        StaffIdentitySecurityNoticeListener listener = new StaffIdentitySecurityNoticeListener(
                recipients, sender);
        UUID assignmentId = UUID.fromString("00000000-0000-0000-0000-000000009403");
        UUID branchId = UUID.fromString("00000000-0000-0000-0000-000000009404");

        listener.onBranchAssigned(new StaffBranchAssigned(
                assignmentId, USER_ID, branchId, ACTOR_ID, "admin", NOW, true));
        listener.onBranchAssignmentEnded(new StaffBranchAssignmentEnded(
                assignmentId, USER_ID, branchId, ACTOR_ID, "admin", NOW, true));

        ArgumentCaptor<StaffIdentitySecurityEmail> notices =
                ArgumentCaptor.forClass(StaffIdentitySecurityEmail.class);
        verify(sender, org.mockito.Mockito.times(2)).sendSecurityNotice(notices.capture());
        org.assertj.core.api.Assertions.assertThat(notices.getAllValues())
                .allSatisfy(notice -> {
                    org.assertj.core.api.Assertions.assertThat(notice.noticeType())
                            .isEqualTo(IdentitySecurityNoticeType.BRANCH_ASSIGNMENTS_CHANGED);
                    org.assertj.core.api.Assertions.assertThat(notice.toString())
                            .doesNotContain("staff@example.test", branchId.toString(), "admin");
                });
        verify(recipients, org.mockito.Mockito.times(2)).findNoticeRecipient(USER_ID);
        verify(recipients, never()).findNoticeRecipient(ACTOR_ID);
    }
}
