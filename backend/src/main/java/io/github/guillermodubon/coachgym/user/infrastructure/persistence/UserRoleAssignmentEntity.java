package io.github.guillermodubon.coachgym.user.infrastructure.persistence;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.Instant;
import jakarta.persistence.Column;

@Entity
@Table(schema = "gym", name = "user_roles")
class UserRoleAssignmentEntity {

    @EmbeddedId
    private UserRoleAssignmentId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccountEntity user;

    @MapsId("roleId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private RoleEntity role;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    protected UserRoleAssignmentEntity() {
    }

    UserRoleAssignmentEntity(UserAccountEntity user, RoleEntity role, Instant grantedAt) {
        this.id = new UserRoleAssignmentId(user.id(), role.id());
        this.user = user;
        this.role = role;
        this.grantedAt = grantedAt;
    }

    RoleEntity role() {
        return role;
    }
}
