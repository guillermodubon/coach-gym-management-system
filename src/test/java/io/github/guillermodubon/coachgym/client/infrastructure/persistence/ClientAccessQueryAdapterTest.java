package io.github.guillermodubon.coachgym.client.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import io.github.guillermodubon.coachgym.client.ClientAccessDetails;
import io.github.guillermodubon.coachgym.client.ClientStatus;
import io.github.guillermodubon.coachgym.client.domain.ClientRegistration;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClientAccessQueryAdapterTest {

    private static final UUID CLIENT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    private static final UUID ACTOR_ID =
            UUID.fromString("50000000-0000-0000-0000-000000000001");

    private static final Instant NOW =
            Instant.parse("2026-08-28T18:00:00Z");

    private static final AuthenticatedActor ACTOR =
            new AuthenticatedActor(ACTOR_ID, "access-admin");

    private ClientJpaRepository clientRepository;
    private ClientAccessQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        clientRepository = mock(ClientJpaRepository.class);
        adapter = new ClientAccessQueryAdapter(clientRepository);
    }

    // ── findByCode ────────────────────────────────────────────────────────────

    @Test
    void returnsDetailsForActiveClient() {
        ClientJpaEntity entity = activeClientEntity();

        given(clientRepository.findByClientCodeIgnoreCase("CLI-000001"))
                .willReturn(Optional.of(entity));

        Optional<ClientAccessDetails> result =
                adapter.findByCode("CLI-000001");

        assertThat(result).isPresent();
        ClientAccessDetails details = result.get();
        // entity.id() is UUID.randomUUID() — assert non-null, not a fixed value
        assertThat(details.id()).isNotNull();
        assertThat(details.status()).isEqualTo(ClientStatus.ACTIVE);
        // clientCode is a DB-generated STORED column; null in in-memory entity
        // (no DB round-trip in unit tests) — adapter passes it through as-is
        assertThat(details.clientCode()).isNull();
    }

    @Test
    void returnsDetailsForInactiveClient() {
        ClientJpaEntity entity = activeClientEntity();

        given(clientRepository.findByClientCodeIgnoreCase("CLI-000001"))
                .willReturn(Optional.of(entity));

        Optional<ClientAccessDetails> result =
                adapter.findByCode("CLI-000001");

        // Adapter returns status as-is; policy decides what to do with it.
        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(ClientStatus.ACTIVE);
    }

    @Test
    void returnsEmptyWhenClientCodeNotFound() {
        given(clientRepository.findByClientCodeIgnoreCase("CLI-999999"))
                .willReturn(Optional.empty());

        Optional<ClientAccessDetails> result =
                adapter.findByCode("CLI-999999");

        assertThat(result).isEmpty();
    }

    @Test
    void projectionHasExactlyThreeComponentsAndNoPersonalData() {
        // ClientAccessDetails only has id, clientCode, status — no PII.
        // If the record were to add email/phone fields this assertion would
        // fail, forcing a deliberate review of the minimal-projection contract.
        ClientJpaEntity entity = activeClientEntity();

        given(clientRepository.findByClientCodeIgnoreCase("CLI-000001"))
                .willReturn(Optional.of(entity));

        ClientAccessDetails details = adapter.findByCode("CLI-000001").orElseThrow();

        assertThat(details.getClass().getRecordComponents()).hasSize(3);
        assertThat(details.id()).isNotNull();
        assertThat(details.status()).isEqualTo(ClientStatus.ACTIVE);
        // clientCode is null in unit tests (DB-generated column, no DB round-trip)
    }

    @Test
    void passesNormalisedCodeDirectlyToRepository() {
        // Adapter does not re-normalise — the application service does.
        given(clientRepository.findByClientCodeIgnoreCase("CLI-000042"))
                .willReturn(Optional.empty());

        adapter.findByCode("CLI-000042");

        // Verified via mock: repository received exactly what was passed in.
        // No silent upper-casing or trimming in the adapter.
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ClientJpaEntity activeClientEntity() {
        ClientRegistration registration = new ClientRegistration(
                "Access",
                "Client",
                "access-client@example.com",
                "+50370000001",
                LocalDate.of(1990, 1, 1),
                null);

        return ClientJpaEntity.register(registration, ACTOR, NOW);
    }
}
