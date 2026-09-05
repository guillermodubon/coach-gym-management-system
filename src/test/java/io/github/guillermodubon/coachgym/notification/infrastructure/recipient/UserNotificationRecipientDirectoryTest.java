package io.github.guillermodubon.coachgym.notification.infrastructure.recipient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.user.ActiveStaffDirectory;
import io.github.guillermodubon.coachgym.user.ActiveStaffMember;
import io.github.guillermodubon.coachgym.user.RoleCode;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserNotificationRecipientDirectoryTest {

    @Mock private ActiveStaffDirectory staffDirectory;

    @Test
    void mapsActiveAdministratorsFromPublicUserContract() {
        UUID id = UUID.randomUUID();
        when(staffDirectory.findActiveByRole(RoleCode.ADMIN)).thenReturn(List.of(
                new ActiveStaffMember(id, "admin-user", Set.of(RoleCode.ADMIN))));
        UserNotificationRecipientDirectory directory =
                new UserNotificationRecipientDirectory(staffDirectory);

        assertThat(directory.findActiveByRole("admin"))
                .singleElement()
                .satisfies(recipient -> {
                    assertThat(recipient.userId()).isEqualTo(id);
                    assertThat(recipient.roles()).containsExactly("ADMIN");
                });
    }

    @Test
    void rejectsRemovedOrUnsupportedRole() {
        UserNotificationRecipientDirectory directory =
                new UserNotificationRecipientDirectory(staffDirectory);
        assertThatThrownBy(() -> directory.findActiveByRole("MAINTENANCE"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
