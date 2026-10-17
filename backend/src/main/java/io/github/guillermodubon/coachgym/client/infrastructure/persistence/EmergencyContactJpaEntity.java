package io.github.guillermodubon.coachgym.client.infrastructure.persistence;

import io.github.guillermodubon.coachgym.client.EmergencyContactDetails;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "gym", name = "emergency_contacts")
class EmergencyContactJpaEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private ClientJpaEntity client;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(nullable = false, length = 100)
    private String relationship;

    @Column(nullable = false, length = 32)
    private String phone;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected EmergencyContactJpaEntity() {
    }

    static EmergencyContactJpaEntity create(
            ClientJpaEntity client,
            String fullName,
            String relationship,
            String phone,
            Instant occurredAt) {
        EmergencyContactJpaEntity contact = new EmergencyContactJpaEntity();
        contact.id = UUID.randomUUID();
        contact.client = client;
        contact.fullName = fullName;
        contact.relationship = relationship;
        contact.phone = phone;
        contact.primary = true;
        contact.createdAt = occurredAt;
        contact.updatedAt = occurredAt;
        return contact;
    }

    EmergencyContactDetails toDetails() {
        return new EmergencyContactDetails(id, fullName, relationship, phone, primary);
    }
}
