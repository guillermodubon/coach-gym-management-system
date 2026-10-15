package io.github.guillermodubon.coachgym.user;

import java.util.Optional;
import java.util.UUID;

/** Notification-only lookup of a provisioned staff member's current email destination. */
public interface StaffIdentityNoticeQuery {

    Optional<StaffIdentityNoticeRecipient> findNoticeRecipient(UUID userId);
}
