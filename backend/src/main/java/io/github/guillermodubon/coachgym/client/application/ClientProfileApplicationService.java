package io.github.guillermodubon.coachgym.client.application;

import io.github.guillermodubon.coachgym.client.ClientOperationalProfile;
import io.github.guillermodubon.coachgym.client.ClientPage;
import io.github.guillermodubon.coachgym.client.ClientSearchQuery;
import io.github.guillermodubon.coachgym.client.ClientStatus;
import io.github.guillermodubon.coachgym.client.ClientStatusHistoryDetails;
import io.github.guillermodubon.coachgym.client.domain.ClientLifecyclePolicy;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.ActiveBranchContextUnavailableException;
import io.github.guillermodubon.coachgym.user.BranchOperationContext;
import io.github.guillermodubon.coachgym.user.BranchOperationContextResolver;
import io.github.guillermodubon.coachgym.user.BranchResourceAuthorizationPolicy;
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
    private final BranchOperationContextResolver branchContextResolver;

    public ClientProfileApplicationService(
            ClientSearchStore searchStore,
            ClientOperationalProfileQuery profileQuery,
            ClientStatusHistoryQuery statusHistoryQuery,
            ClientMutationStore mutationStore,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            BranchOperationContextResolver branchContextResolver) {
        this.searchStore = Objects.requireNonNull(searchStore);
        this.profileQuery = Objects.requireNonNull(profileQuery);
        this.statusHistoryQuery = Objects.requireNonNull(statusHistoryQuery);
        this.mutationStore = Objects.requireNonNull(mutationStore);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
        this.branchContextResolver = branchContextResolver;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public ClientPage findAll(ClientSearchQuery query) {
        return searchStore.findAll(query == null ? ClientSearchQuery.defaults() : query);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public ClientPage findAll(ClientSearchQuery query, AuthenticatedActor actor) {
        ClientSearchQuery effective = query == null ? ClientSearchQuery.defaults() : query;
        if (branchContextResolver == null) {
            throw new ActiveBranchContextUnavailableException();
        }
        BranchOperationContext context = resolveContext(actor);
        UUID branchId = BranchResourceAuthorizationPolicy.requireListBranch(
                context, effective.branchId());
        return searchStore.findAll(effective, branchId);
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
    public ClientOperationalProfile findProfile(UUID clientId, AuthenticatedActor actor) {
        requireId(clientId);
        UUID branchId = branchForResource(actor);
        return profileQuery.findById(clientId, branchId)
                .orElseThrow(() -> new ClientNotFoundException(clientId));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public List<ClientStatusHistoryDetails> findStatusHistory(UUID clientId) {
        requireId(clientId);
        findProfile(clientId);
        return statusHistoryQuery.findByClientId(clientId);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public List<ClientStatusHistoryDetails> findStatusHistory(
            UUID clientId,
            AuthenticatedActor actor) {
        requireId(clientId);
        findProfile(clientId, actor);
        return statusHistoryQuery.findByClientId(clientId, branchForResource(actor));
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
        UUID branchId = branchForResource(actor);
        ClientOperationalProfile current = findProfile(clientId, actor);
        Instant occurredAt = clock.instant();
        mutationStore.update(clientId, command, actor, occurredAt, branchId);
        eventPublisher.publishEvent(new ClientProfileChangedEvent(
                clientId,
                ClientProfileChangedEvent.ChangeType.PROFILE_UPDATED,
                current.status(),
                current.status(),
                actor.id(),
                occurredAt,
                current.homeBranchId()));
        return findProfile(clientId, actor);
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
        UUID branchId = branchForResource(actor);
        ClientOperationalProfile current = findProfile(clientId, actor);
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
                occurredAt,
                branchId);
        eventPublisher.publishEvent(new ClientProfileChangedEvent(
                clientId,
                type,
                expectedCurrent,
                requested,
                actor.id(),
                occurredAt,
                current.homeBranchId()));
        return findProfile(clientId, actor);
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

    private BranchOperationContext resolveContext(AuthenticatedActor actor) {
        requireActor(actor);
        return branchContextResolver.resolveOperation(actor.id());
    }

    private UUID branchForResource(AuthenticatedActor actor) {
        if (branchContextResolver == null) {
            return null;
        }
        return BranchResourceAuthorizationPolicy.requireActiveBranch(resolveContext(actor));
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
