package io.github.guillermodubon.coachgym.client.application;

import io.github.guillermodubon.coachgym.client.ClientDetails;
import io.github.guillermodubon.coachgym.client.ClientRegistered;
import io.github.guillermodubon.coachgym.client.domain.ClientRegistration;
import io.github.guillermodubon.coachgym.client.domain.EmergencyContactRegistration;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientApplicationService {

    private final ClientStore clientStore;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ClientApplicationService(
            ClientStore clientStore,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.clientStore = clientStore;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
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
        try {
            client = clientStore.register(registration, actor, occurredAt);
        } catch (DataIntegrityViolationException exception) {
            if (isUniqueConstraintViolation(exception)) {
                throw new DuplicateClientException();
            }
            throw exception;
        }
        eventPublisher.publishEvent(new ClientRegistered(
                client.id(), client.clientCode(), actor.id(), actor.username(), occurredAt));
        return client;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
    public ClientDetails findById(UUID id) {
        return clientStore.findById(id).orElseThrow(() -> new ClientNotFoundException(id));
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
