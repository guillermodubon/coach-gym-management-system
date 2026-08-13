package io.github.guillermodubon.coachgym.user.infrastructure.persistence;

import io.github.guillermodubon.coachgym.user.AuthenticatedUser;
import io.github.guillermodubon.coachgym.user.AuthenticationUserQuery;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.SuccessfulLoginRecorder;
import io.github.guillermodubon.coachgym.user.application.InitialAdministrator;
import io.github.guillermodubon.coachgym.user.application.UserAccountStore;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class UserAccountPersistenceAdapter
        implements AuthenticationUserQuery, SuccessfulLoginRecorder, UserAccountStore {

    private final UserAccountJpaRepository userRepository;
    private final RoleJpaRepository roleRepository;

    UserAccountPersistenceAdapter(UserAccountJpaRepository userRepository, RoleJpaRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthenticatedUser> findActiveUserByIdentifier(String identifier) {
        return userRepository.findActiveByIdentifier(identifier.trim())
                .map(UserAccountEntity::toAuthenticatedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAnyUsers() {
        return userRepository.count() > 0;
    }

    @Override
    @Transactional
    public void createInitialAdministrator(InitialAdministrator administrator, Instant grantedAt) {
        RoleEntity administratorRole = roleRepository.findByRoleCode(RoleCode.ADMIN)
                .orElseThrow(() -> new IllegalStateException("The ADMIN system role is missing."));
        UserAccountEntity user = UserAccountEntity.initialAdministrator(
                UUID.randomUUID(),
                administrator.username(),
                administrator.email(),
                administrator.encodedPassword(),
                administrator.firstName(),
                administrator.lastName(),
                administratorRole,
                grantedAt);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void recordSuccessfulLogin(UUID userId, Instant occurredAt) {
        userRepository.findById(userId).ifPresent(user -> user.recordSuccessfulLogin(occurredAt));
    }
}
