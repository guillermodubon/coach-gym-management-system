package io.github.guillermodubon.coachgym.user.infrastructure.persistence;

import io.github.guillermodubon.coachgym.user.AuthenticatedUser;
import io.github.guillermodubon.coachgym.user.RoleCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(schema = "gym", name = "users")
class UserAccountEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserRoleAssignmentEntity> roleAssignments = new LinkedHashSet<>();

    protected UserAccountEntity() {
    }

    private UserAccountEntity(
            UUID id,
            String username,
            String email,
            String passwordHash,
            String firstName,
            String lastName) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.status = UserStatus.ACTIVE;
    }

    static UserAccountEntity initialAdministrator(
            UUID id,
            String username,
            String email,
            String passwordHash,
            String firstName,
            String lastName,
            RoleEntity administratorRole,
            Instant grantedAt) {
        UserAccountEntity user = new UserAccountEntity(id, username, email, passwordHash, firstName, lastName);
        user.roleAssignments.add(new UserRoleAssignmentEntity(user, administratorRole, grantedAt));
        return user;
    }

    UUID id() {
        return id;
    }

    void recordSuccessfulLogin(Instant occurredAt) {
        this.lastLoginAt = occurredAt;
    }

    AuthenticatedUser toAuthenticatedUser() {
        Set<RoleCode> roles = roleAssignments.stream()
                .map(UserRoleAssignmentEntity::role)
                .map(RoleEntity::roleCode)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new AuthenticatedUser(id, username, passwordHash, firstName + " " + lastName, roles);
    }
}
