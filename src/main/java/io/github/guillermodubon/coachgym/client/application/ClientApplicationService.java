package io.github.guillermodubon.coachgym.client.application;

import io.github.guillermodubon.coachgym.client.ClientDetails;
import io.github.guillermodubon.coachgym.client.ClientQuery;
import io.github.guillermodubon.coachgym.client.ClientRegistered;
import io.github.guillermodubon.coachgym.client.domain.ClientRegistration;
import io.github.guillermodubon.coachgym.client.domain.EmergencyContactRegistration;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import io.github.guillermodubon.coachgym.user.BranchOperationContext;
import io.github.guillermodubon.coachgym.user.BranchOperationContextResolver;
import io.github.guillermodubon.coachgym.user.BranchResourceAuthorizationPolicy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientApplicationService implements ClientQuery {

    private final ClientStore clientStore;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final BranchOperationContextResolver branchContextResolver;

    @Autowired
    public ClientApplicationService(
            ClientStore clientStore,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            BranchOperationContextResolver branchContextResolver) {
        this.clientStore = clientStore;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.branchContextResolver = branchContextResolver;
    }

    public ClientApplicationService(
            ClientStore clientStore,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this(clientStore, eventPublisher, clock, null);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public ClientDetails register(RegisterClientCommand command, AuthenticatedActor actor) {
        ClientRegistration registration = toRegistration(command);
        if (registration.email() != null && clientStore.existsByEmail(registration.email())) {
            throw new DuplicateClientException();
        }

        Instant occurredAt = clock.instant();
        ClientDetails client;
        UUID branchId = branchId(actor);
        try {
            client = clientStore.register(registration, actor, occurredAt, branchId);
        } catch (DataIntegrityViolationException exception) {
            if (isUniqueConstraintViolation(exception)) {
                throw new DuplicateClientException();
            }
            throw exception;
        }
        eventPublisher.publishEvent(new ClientRegistered(
                client.id(), client.clientCode(), actor.id(), actor.username(), occurredAt,
                client.homeBranchId()));
        return client;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public ClientDetails findById(UUID id) {
        return clientStore.findById(id)
                .orElseThrow(() -> new ClientNotFoundException(id));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public ClientDetails findById(UUID id, AuthenticatedActor actor) {
        if (branchContextResolver == null) {
            return findById(id);
        }
        BranchOperationContext context = branchContextResolver.resolveOperation(actor.id());
        return clientStore.findById(
                        id,
                        BranchResourceAuthorizationPolicy.requireActiveBranch(context))
                .orElseThrow(() -> new ClientNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClientDetails> findClientById(
            UUID clientId) {

        if (clientId == null) {
            return Optional.empty();
        }

        return clientStore.findById(clientId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClientDetails> findClientById(
            UUID clientId,
            UUID homeBranchId) {
        if (clientId == null || homeBranchId == null) {
            return Optional.empty();
        }
        return clientStore.findById(clientId, homeBranchId);
    }

    private ClientRegistration toRegistration(RegisterClientCommand command) {
        EmergencyContactRegistration emergencyContact = Optional.ofNullable(command.emergencyContact())
                .map(contact -> new EmergencyContactRegistration(
                        contact.fullName(), contact.relationship(), contact.phone()))
                .orElse(null);
        LocalDate today = LocalDate.now(clock);
        return ClientRegistration.create(
                command.firstName(),
                command.lastName(),
                command.email(),
                command.phone(),
                command.dateOfBirth(),
                emergencyContact,
                today);
    }

    private UUID branchId(AuthenticatedActor actor) {
        if (branchContextResolver == null) {
            return null;
        }
        if (actor == null || actor.id() == null) {
            throw new ClientValidationException("Authenticated actor is required.");
        }
        BranchOperationContext context = branchContextResolver.resolveOperation(actor.id());
        return BranchResourceAuthorizationPolicy.requireCreationBranch(context, null);
    }

    private static boolean isUniqueConstraintViolation(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
