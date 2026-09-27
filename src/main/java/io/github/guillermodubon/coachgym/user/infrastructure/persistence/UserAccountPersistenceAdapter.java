package io.github.guillermodubon.coachgym.user.infrastructure.persistence;

import io.github.guillermodubon.coachgym.user.AuthenticatedUser;
import io.github.guillermodubon.coachgym.user.AuthenticationUserQuery;
import io.github.guillermodubon.coachgym.user.RoleCode;
import io.github.guillermodubon.coachgym.user.StaffAccountSecurityState;
import io.github.guillermodubon.coachgym.user.StaffIdentityNoticeQuery;
import io.github.guillermodubon.coachgym.user.StaffIdentityNoticeRecipient;
import io.github.guillermodubon.coachgym.user.SuccessfulLoginRecorder;
import io.github.guillermodubon.coachgym.user.application.InitialAdministrator;
import io.github.guillermodubon.coachgym.user.application.UserAccountStore;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class UserAccountPersistenceAdapter
        implements AuthenticationUserQuery, StaffIdentityNoticeQuery,
        SuccessfulLoginRecorder, UserAccountStore {

    private final UserAccountJpaRepository userRepository;
    private final RoleJpaRepository roleRepository;
    private final InitialAdministratorScopeProvisioner scopeProvisioner;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    UserAccountPersistenceAdapter(
            UserAccountJpaRepository userRepository,
            RoleJpaRepository roleRepository,
            InitialAdministratorScopeProvisioner scopeProvisioner,
            NamedParameterJdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.scopeProvisioner = scopeProvisioner;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthenticatedUser> findActiveUserByIdentifier(String identifier) {
        return userRepository.findActiveByIdentifier(identifier.trim())
                .map(UserAccountEntity::toAuthenticatedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StaffAccountSecurityState> findAccountSecurityState(UUID userId) {
        return userRepository.findById(userId).map(UserAccountEntity::securityState);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StaffIdentityNoticeRecipient> findNoticeRecipient(UUID userId) {
        return jdbcTemplate.query("""
                select email, first_name, last_name
                  from gym.users
                 where id = :userId
                """, new MapSqlParameterSource("userId", userId),
                rs -> rs.next()
                        ? Optional.of(new StaffIdentityNoticeRecipient(
                                rs.getString("email"),
                                rs.getString("first_name") + " " + rs.getString("last_name")))
                        : Optional.empty());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAnyUsers() {
        return userRepository.count() > 0;
    }

    @Override
    @Transactional
    public void lockBootstrapLifecycle() {
        jdbcTemplate.query("select pg_advisory_xact_lock(1162231123, 41)",
                new MapSqlParameterSource(), rs -> {
                    while (rs.next()) {
                        rs.getObject(1);
                    }
                    return null;
                });
    }

    @Override
    @Transactional
    public UUID createInitialAdministrator(InitialAdministrator administrator, Instant grantedAt) {
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
        userRepository.flush();
        scopeProvisioner.provision(user.id(), grantedAt);
        return user.id();
    }

    @Override
    @Transactional
    public void recordSuccessfulLogin(UUID userId, Instant occurredAt) {
        userRepository.findById(userId).ifPresent(user -> user.recordSuccessfulLogin(occurredAt));
    }
}
