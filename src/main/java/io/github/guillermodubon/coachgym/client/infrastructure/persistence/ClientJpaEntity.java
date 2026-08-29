package io.github.guillermodubon.coachgym.client.infrastructure.persistence;

import io.github.guillermodubon.coachgym.client.ClientDetails;
import io.github.guillermodubon.coachgym.client.ClientStatus;
import io.github.guillermodubon.coachgym.client.EmergencyContactDetails;
import io.github.guillermodubon.coachgym.client.domain.ClientRegistration;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(schema = "gym", name = "clients")
class ClientJpaEntity {

    @Id
    private UUID id;

    @Column(name = "client_number", nullable = false, insertable = false, updatable = false)
    private Long clientNumber;

    @Column(name = "client_code", nullable = false, insertable = false, updatable = false, length = 32)
    private String clientCode;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(length = 254)
    private String email;

    @Column(nullable = false, length = 32)
    private String phone;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClientStatus status;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "updated_by_user_id", nullable = false)
    private UUID updatedByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<EmergencyContactJpaEntity> emergencyContacts = new ArrayList<>();

    protected ClientJpaEntity() {
    }

    static ClientJpaEntity register(
            ClientRegistration registration,
            AuthenticatedActor actor,
            Instant occurredAt) {
        ClientJpaEntity client = new ClientJpaEntity();
        client.id = UUID.randomUUID();
        client.firstName = registration.firstName();
        client.lastName = registration.lastName();
        client.email = registration.email();
        client.phone = registration.phone();
        client.dateOfBirth = registration.dateOfBirth();
        client.status = ClientStatus.ACTIVE;
        client.createdByUserId = actor.id();
        client.updatedByUserId = actor.id();
        client.createdAt = occurredAt;
        client.updatedAt = occurredAt;
        if (registration.emergencyContact() != null) {
            client.emergencyContacts.add(EmergencyContactJpaEntity.create(
                    client,
                    registration.emergencyContact().fullName(),
                    registration.emergencyContact().relationship(),
                    registration.emergencyContact().phone(),
                    occurredAt));
        }
        return client;
    }

    ClientDetails toDetails() {
        EmergencyContactDetails emergencyContact = emergencyContacts.stream()
                .findFirst()
                .map(EmergencyContactJpaEntity::toDetails)
                .orElse(null);
        return new ClientDetails(
                id,
                clientCode,
                firstName,
                lastName,
                email,
                phone,
                dateOfBirth,
                status,
                createdAt,
                updatedAt,
                emergencyContact);
    }

    UUID id() {
        return id;
    }

    String clientCode() {
        return clientCode;
    }

    ClientStatus status() {
        return status;
    }
}
