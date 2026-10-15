package io.github.guillermodubon.coachgym.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationRecipientDirectoryContractTest {

    @Test
    void recipientProjectionUsesOnlyCurrentRoles() {
        NotificationRecipient recipient = new NotificationRecipient(
                UUID.randomUUID(), " admin-user ", Set.of("ADMIN"));
        assertThat(recipient.username()).isEqualTo("admin-user");
        assertThat(recipient.roles()).containsExactly("ADMIN");
        assertThat(recipient.roles()).doesNotContain("MAINTENANCE");
    }

    @Test
    void recipientRolesAreImmutable() {
        NotificationRecipient recipient = new NotificationRecipient(
                UUID.randomUUID(), "reception-user", Set.of("RECEPTIONIST"));
        assertThatThrownBy(() -> recipient.roles().add("ADMIN"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
