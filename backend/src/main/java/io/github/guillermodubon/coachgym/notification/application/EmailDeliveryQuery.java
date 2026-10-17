package io.github.guillermodubon.coachgym.notification.application;

import io.github.guillermodubon.coachgym.notification.EmailDeliveryDetails;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryAttemptDetails;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read boundary for canonical transactional email delivery metadata. */
public interface EmailDeliveryQuery {

    Optional<EmailDeliveryDetails> findById(UUID deliveryId);

    default Optional<EmailDeliveryDetails> findById(UUID deliveryId, UUID branchId) {
        return findById(deliveryId);
    }

    Optional<EmailDeliveryDetails> findByIdempotencyKeyDigest(String idempotencyKeyDigest);

    EmailDeliveryPage findAll(EmailDeliverySearchQuery query);

    default EmailDeliveryPage findAll(EmailDeliverySearchQuery query, UUID branchId) {
        return findAll(query);
    }

    /** Returns immutable attempts in chronological order. */
    default List<EmailDeliveryAttemptDetails> findAttempts(UUID deliveryId) {
        return List.of();
    }

    default List<EmailDeliveryAttemptDetails> findAttempts(
            UUID deliveryId, UUID branchId) {
        return findAttempts(deliveryId);
    }
}
