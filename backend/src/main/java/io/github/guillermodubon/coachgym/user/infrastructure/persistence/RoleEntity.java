package io.github.guillermodubon.coachgym.user.infrastructure.persistence;

import io.github.guillermodubon.coachgym.user.RoleCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(schema = "gym", name = "roles")
class RoleEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_code", nullable = false)
    private RoleCode roleCode;

    protected RoleEntity() {
    }

    UUID id() {
        return id;
    }

    RoleCode roleCode() {
        return roleCode;
    }
}
