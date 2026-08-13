package io.github.guillermodubon.coachgym.user.application;

import io.github.guillermodubon.coachgym.configuration.InitialAdminProperties;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provisions the first administrator only when an explicitly enabled empty system starts.
 */
@Component
public class InitialAdminProvisioner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(InitialAdminProvisioner.class);

    private final InitialAdminProperties properties;
    private final UserAccountStore userAccountStore;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public InitialAdminProvisioner(
            InitialAdminProperties properties,
            UserAccountStore userAccountStore,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.properties = properties;
        this.userAccountStore = userAccountStore;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        provision();
    }

    /**
     * Idempotently creates the configured administrator when the user registry is empty.
     */
    @Transactional
    public void provision() {
        if (!properties.enabled()) {
            return;
        }

        if (userAccountStore.hasAnyUsers()) {
            LOGGER.info("Initial administrator provisioning skipped because staff accounts already exist.");
            return;
        }

        Instant provisionedAt = clock.instant();
        InitialAdministrator administrator = new InitialAdministrator(
                properties.username().trim(),
                properties.email().trim(),
                passwordEncoder.encode(properties.password()),
                properties.firstName().trim(),
                properties.lastName().trim());

        userAccountStore.createInitialAdministrator(administrator, provisionedAt);
        LOGGER.info("Initial administrator account provisioned for username '{}'.", administrator.username());
    }
}
