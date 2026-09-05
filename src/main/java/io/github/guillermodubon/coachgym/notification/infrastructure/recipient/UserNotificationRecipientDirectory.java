package io.github.guillermodubon.coachgym.notification.infrastructure.recipient;

import io.github.guillermodubon.coachgym.notification.application.NotificationRecipient;
import io.github.guillermodubon.coachgym.notification.application.NotificationRecipientDirectory;
import io.github.guillermodubon.coachgym.user.ActiveStaffDirectory;
import io.github.guillermodubon.coachgym.user.ActiveStaffMember;
import io.github.guillermodubon.coachgym.user.RoleCode;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class UserNotificationRecipientDirectory implements NotificationRecipientDirectory {

    private final ActiveStaffDirectory activeStaffDirectory;

    UserNotificationRecipientDirectory(ActiveStaffDirectory activeStaffDirectory) {
        this.activeStaffDirectory = activeStaffDirectory;
    }

    @Override
    public Optional<NotificationRecipient> findActiveById(UUID userId) {
        return activeStaffDirectory.findActiveById(userId)
                .map(UserNotificationRecipientDirectory::toRecipient);
    }

    @Override
    public List<NotificationRecipient> findActiveByRole(String roleCode) {
        RoleCode role = role(roleCode);
        return activeStaffDirectory.findActiveByRole(role).stream()
                .map(UserNotificationRecipientDirectory::toRecipient)
                .toList();
    }

    private static NotificationRecipient toRecipient(ActiveStaffMember member) {
        return new NotificationRecipient(
                member.userId(),
                member.username(),
                member.roles().stream().map(Enum::name).collect(
                        java.util.stream.Collectors.toUnmodifiableSet()));
    }

    private static RoleCode role(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            throw new IllegalArgumentException("Notification recipient role is required.");
        }
        try {
            return RoleCode.valueOf(roleCode.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unsupported notification recipient role: " + roleCode + ".",
                    exception);
        }
    }
}
