package io.github.guillermodubon.coachgym.notification.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public application port for resolving active notification recipients. */
public interface NotificationRecipientDirectory {

    Optional<NotificationRecipient> findActiveById(UUID userId);

    List<NotificationRecipient> findActiveByRole(String roleCode);
}
