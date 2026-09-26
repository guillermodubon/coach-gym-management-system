package io.github.guillermodubon.coachgym.notification.application;

import io.github.guillermodubon.coachgym.notification.NotificationDetails;
import io.github.guillermodubon.coachgym.notification.NotificationUnreadCount;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.BranchOperationContextResolver;
import io.github.guillermodubon.coachgym.user.BranchResourceAuthorizationPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recipient-scoped application service for the internal notification inbox. */
@Service
public class NotificationApplicationService {

    private final NotificationStore notificationStore;
    private final Clock clock;
    private final BranchOperationContextResolver branchContextResolver;

    public NotificationApplicationService(
            NotificationStore notificationStore,
            Clock clock) {
        this(notificationStore, clock, null);
    }

    @Autowired
    public NotificationApplicationService(
            NotificationStore notificationStore,
            Clock clock,
            BranchOperationContextResolver branchContextResolver) {
        this.notificationStore = Objects.requireNonNull(
                notificationStore, "Notification store is required.");
        this.clock = Objects.requireNonNull(clock, "Application clock is required.");
        this.branchContextResolver = branchContextResolver;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public NotificationPage findAll(
            NotificationSearchQuery query,
            AuthenticatedActor actor) {
        requireActor(actor);
        UUID branchId = branchId(actor);
        return branchContextResolver == null
                ? notificationStore.findAllByRecipientUserId(
                        actor.id(), Objects.requireNonNull(query, "Notification search query is required."))
                : notificationStore.findAllByRecipientUserId(
                        actor.id(), Objects.requireNonNull(query, "Notification search query is required."), branchId);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public NotificationDetails findById(
            UUID notificationId,
            AuthenticatedActor actor) {
        requireId(notificationId);
        requireActor(actor);
        Optional<NotificationDetails> details = branchContextResolver == null
                ? notificationStore.findByIdAndRecipientUserId(notificationId, actor.id())
                : notificationStore.findByIdAndRecipientUserId(
                        notificationId, actor.id(), branchId(actor));
        return details
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public NotificationUnreadCount countUnread(AuthenticatedActor actor) {
        requireActor(actor);
        return new NotificationUnreadCount(
                branchContextResolver == null
                        ? notificationStore.countUnreadByRecipientUserId(actor.id())
                        : notificationStore.countUnreadByRecipientUserId(
                                actor.id(), branchId(actor)));
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public NotificationDetails markAsRead(
            UUID notificationId,
            AuthenticatedActor actor) {
        requireId(notificationId);
        requireActor(actor);
        return branchContextResolver == null
                ? notificationStore.markAsRead(notificationId, actor.id(), now())
                : notificationStore.markAsRead(
                        notificationId, actor.id(), now(), branchId(actor));
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public NotificationUnreadCount markAllAsRead(AuthenticatedActor actor) {
        requireActor(actor);
        int ignored = branchContextResolver == null
                ? notificationStore.markAllAsRead(actor.id(), now())
                : notificationStore.markAllAsRead(actor.id(), now(), branchId(actor));
        return new NotificationUnreadCount(
                branchContextResolver == null
                        ? notificationStore.countUnreadByRecipientUserId(actor.id())
                        : notificationStore.countUnreadByRecipientUserId(
                                actor.id(), branchId(actor)));
    }

    private static void requireId(UUID notificationId) {
        if (notificationId == null) {
            throw new IllegalArgumentException("Notification id is required.");
        }
    }

    private static void requireActor(AuthenticatedActor actor) {
        Objects.requireNonNull(actor, "Authenticated actor is required.");
        if (actor.id() == null) {
            throw new IllegalArgumentException("Authenticated actor id is required.");
        }
    }

    private Instant now() {
        return clock.instant();
    }

    private UUID branchId(AuthenticatedActor actor) {
        if (branchContextResolver == null) {
            return null;
        }
        return BranchResourceAuthorizationPolicy.requireActiveBranch(
                branchContextResolver.resolveOperation(actor.id()));
    }
}
