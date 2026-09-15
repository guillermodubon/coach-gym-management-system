package io.github.guillermodubon.coachgym.configuration.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicy;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyDetails;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyQuery;
import io.github.guillermodubon.coachgym.configuration.application.AccessPaymentPolicyStore;
import io.github.guillermodubon.coachgym.configuration.application.AccessPaymentPolicyVersionConflictException;
import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JdbcAccessPaymentPolicyPersistenceIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    @Autowired
    private AccessPaymentPolicyQuery policyQuery;

    @Autowired
    private AccessPaymentPolicyStore policyStore;

    @BeforeEach
    void resetPolicyToMigrationDefault() {
        jdbcTemplate.update("""
                update gym.gym_settings
                set require_confirmed_payment_for_access = false,
                    updated_by_user_id = null,
                    version = 0
                where id = 1
                """);
    }

    @Test
    void readsAndUpdatesTheSingletonWithServerOwnedMetadata() {
        AccessPaymentPolicyDetails before = policyQuery.findCurrent();
        Instant occurredAt = Instant.parse("2026-09-14T18:30:00Z");

        AccessPaymentPolicyDetails after = policyStore.update(
                AccessPaymentPolicy.enabled(),
                before.version(),
                adminId,
                occurredAt);

        assertThat(after.requireConfirmedPaymentForAccess()).isTrue();
        assertThat(after.version()).isEqualTo(before.version() + 1);
        assertThat(after.updatedByUserId()).isEqualTo(adminId);
        // The existing gym_settings trigger is authoritative for updated_at
        // and deliberately uses the database clock.
        assertThat(after.updatedAt()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "select display_name from gym.gym_settings where id = 1",
                String.class)).isEqualTo("Coach Gym");
    }

    @Test
    void rejectsAStaleVersionWithoutChangingThePolicy() {
        AccessPaymentPolicyDetails before = policyQuery.findCurrent();

        assertThatThrownBy(() -> policyStore.update(
                AccessPaymentPolicy.enabled(),
                before.version() + 1,
                adminId,
                Instant.parse("2026-09-14T18:30:00Z")))
                .isInstanceOf(AccessPaymentPolicyVersionConflictException.class);

        AccessPaymentPolicyDetails after = policyQuery.findCurrent();
        assertThat(after).isEqualTo(before);
    }
}
