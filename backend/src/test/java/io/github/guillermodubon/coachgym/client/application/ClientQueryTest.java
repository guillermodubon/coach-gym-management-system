package io.github.guillermodubon.coachgym.client.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.client.ClientDetails;
import io.github.guillermodubon.coachgym.client.ClientStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ClientQueryTest {

    private static final UUID CLIENT_ID =
            UUID.fromString(
                    "8f5d920f-c4cf-47cf-b006-a7cd73f42975");

    private static final Instant NOW =
            Instant.parse("2026-08-16T18:00:00Z");

    private static final Clock CLOCK =
            Clock.fixed(
                    NOW,
                    ZoneOffset.UTC);

    @Mock
    private ClientStore clientStore;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ClientApplicationService service;

    @BeforeEach
    void setUp() {
        service =
                new ClientApplicationService(
                        clientStore,
                        eventPublisher,
                        CLOCK);
    }

    @Test
    void returnsClientThroughPublicQueryBoundary() {
        ClientDetails client =
                client(ClientStatus.ACTIVE);

        when(clientStore.findById(CLIENT_ID))
                .thenReturn(Optional.of(client));

        Optional<ClientDetails> result =
                service.findClientById(CLIENT_ID);

        assertThat(result)
                .contains(client);

        verify(clientStore)
                .findById(CLIENT_ID);
    }

    @Test
    void returnsInactiveClientForCallerValidation() {
        ClientDetails client =
                client(ClientStatus.INACTIVE);

        when(clientStore.findById(CLIENT_ID))
                .thenReturn(Optional.of(client));

        Optional<ClientDetails> result =
                service.findClientById(CLIENT_ID);

        assertThat(result)
                .isPresent()
                .get()
                .extracting(ClientDetails::status)
                .isEqualTo(ClientStatus.INACTIVE);

        verify(clientStore)
                .findById(CLIENT_ID);
    }

    @Test
    void returnsEmptyForUnknownClient() {
        when(clientStore.findById(CLIENT_ID))
                .thenReturn(Optional.empty());

        Optional<ClientDetails> result =
                service.findClientById(CLIENT_ID);

        assertThat(result)
                .isEmpty();

        verify(clientStore)
                .findById(CLIENT_ID);
    }

    @Test
    void returnsEmptyForMissingClientIdentifier() {
        Optional<ClientDetails> result =
                service.findClientById(null);

        assertThat(result)
                .isEmpty();

        verify(clientStore, never())
                .findById(
                        org.mockito.ArgumentMatchers.any());
    }

    private static ClientDetails client(
            ClientStatus status) {

        return new ClientDetails(
                CLIENT_ID,
                "CLI-000001",
                "Ana",
                "Martinez",
                "ana@example.com",
                "+50370000000",
                LocalDate.of(1995, 4, 12),
                status,
                NOW.minusSeconds(3_600),
                NOW,
                null);
    }
}
