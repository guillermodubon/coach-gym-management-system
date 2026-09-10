package io.github.guillermodubon.coachgym.client.application;

import io.github.guillermodubon.coachgym.client.ClientOperationalProfile;
import io.github.guillermodubon.coachgym.client.ClientPage;
import io.github.guillermodubon.coachgym.client.ClientSearchQuery;
import io.github.guillermodubon.coachgym.client.ClientStatus;
import io.github.guillermodubon.coachgym.client.ClientStatusHistoryDetails;
import io.github.guillermodubon.coachgym.client.domain.ClientLifecyclePolicy;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Search, profile, update, and administrative lifecycle use cases. */
@Service
public class ClientProfileApplicationService {

    private final ClientSearchStore searchStore;
    private final ClientOperationalProfileQuery profileQuery;
    private final ClientStatusHistoryQuery statusHistoryQuery;
    private final ClientMutationStore mutationStore;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ClientProfileApplicationService(
            ClientSearchStore searchStore,
            ClientOperationalProfileQuery profileQuery,
            ClientStatusHistoryQuery statusHistoryQuery,
            ClientMutationStore mutationStore,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.searchStore = Objects.requireNonNull(searchStore);
        this.profileQuery = Objects.requireNonNull(profileQuery);
        this.statusHistoryQuery = Objects.requireNonNull(statusHistoryQuery);
        this.mutationStore = Objects.requireNonNull(mutationStore);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public ClientPage findAll(ClientSearchQuery query) {
        return searchStore.findAll(query == null ? ClientSearchQuery.defaults() : query);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public ClientOperationalProfile findProfile(UUID clientId) {
        requireId(clientId);
        return profileQuery.findById(clientId)
                .orElseThrow(() -> new ClientNotFoundException(clientId));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public List<ClientStatusHistoryDetails> findStatusHistory(UUID clientId) {
        requireId(clientId);
        findProfile(clientId);
        return statusHistoryQuery.findByClientId(clientId);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public ClientOperationalProfile update(
            UUID clientId,
            UpdateClientCommand command,
            AuthenticatedActor actor) {
        requireId(clientId);
        Objects.requireNonNull(command, "Client update command is required.");
        requireActor(actor);
        ClientOperationalProfile current = findProfile(clientId);
        Instant occurredAt = clock.instant();
        mutationStore.update(clientId, command, actor, occurredAt);
        eventPublisher.publishEvent(new ClientProfileChangedEvent(
                clientId,
                ClientProfileChangedEvent.ChangeType.PROFILE_UPDATED,
                current.status(),
                current.status(),
                actor.id(),
                occurredAt));
        return findProfile(clientId);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ClientOperationalProfile deactivate(
            UUID clientId,
            DeactivateClientCommand command,
            AuthenticatedActor actor) {
        Objects.requireNonNull(command, "Client deactivation command is required.");
        return changeStatus(
                clientId,
                ClientStatus.ACTIVE,
                ClientStatus.INACTIVE,
                command.reason(),
                command.expectedVersion(),
                actor,
                ClientProfileChangedEvent.ChangeType.DEACTIVATED);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ClientOperationalProfile reactivate(
            UUID clientId,
            ReactivateClientCommand command,
            AuthenticatedActor actor) {
        Objects.requireNonNull(command, "Client reactivation command is required.");
        return changeStatus(
                clientId,
                ClientStatus.INACTIVE,
                ClientStatus.ACTIVE,
                command.reason(),
                command.expectedVersion(),
                actor,
                ClientProfileChangedEvent.ChangeType.REACTIVATED);
    }

    private ClientOperationalProfile changeStatus(
            UUID clientId,
            ClientStatus expectedCurrent,
            ClientStatus requested,
            String reason,
            long expectedVersion,
            AuthenticatedActor actor,
            ClientProfileChangedEvent.ChangeType type) {
        requireId(clientId);
        requireActor(actor);
        ClientOperationalProfile current = findProfile(clientId);
        validateLifecycleTransition(
                clientId,
                current.status(),
                requested);
        Instant occurredAt = clock.instant();
        mutationStore.changeStatus(
                clientId,
                expectedCurrent,
                requested,
                reason,
                expectedVersion,
                actor,
                occurredAt);
        eventPublisher.publishEvent(new ClientProfileChangedEvent(
                clientId,
                type,
                expectedCurrent,
                requested,
                actor.id(),
                occurredAt));
        return findProfile(clientId);
    }

    private static void requireId(UUID id) {
        if (id == null) {
            throw new ClientValidationException("Client id is required.");
        }
    }

    private static void requireActor(AuthenticatedActor actor) {
        if (actor == null || actor.id() == null) {
            throw new ClientValidationException("Authenticated actor is required.");
        }
    }

    private static void validateLifecycleTransition(
            UUID clientId,
            ClientStatus currentStatus,
            ClientStatus requestedStatus) {

        if (requestedStatus == ClientStatus.INACTIVE) {
            ClientLifecyclePolicy.requireDeactivationAllowed(
                    clientId,
                    currentStatus);
            return;
        }

        if (requestedStatus == ClientStatus.ACTIVE) {
            ClientLifecyclePolicy.requireReactivationAllowed(
                    clientId,
                    currentStatus);
            return;
        }

        throw new ClientStateConflictException(
                clientId,
                currentStatus,
                requestedStatus);
    }
}
