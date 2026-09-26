package io.github.guillermodubon.coachgym.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.configuration.application.AccessPaymentPolicyApplicationService;
import io.github.guillermodubon.coachgym.configuration.application.UpdateAccessPaymentPolicyCommand;
import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

class AccessPaymentPolicyApplicationServiceIntegrationTest
        extends AbstractIncidentApiIntegrationTest {

    @Autowired
    private AccessPaymentPolicyApplicationService policyService;

    @BeforeEach
    void resetPolicyAndAudit() {
        jdbcTemplate.update("delete from gym.audit_entries where resource_type = 'SETTINGS'");
        jdbcTemplate.update("""
                update gym.gym_settings
                set require_confirmed_payment_for_access = false,
                    updated_by_user_id = null,
                    version = 0
                where id = 1
                """);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminUpdatePersistsOneSafeAuditEntry() {
        var result = policyService.update(
                new UpdateAccessPaymentPolicyCommand(true, 0),
                new AccessPaymentPolicyActor(adminId, ADMIN_USERNAME));

        assertThat(result.requireConfirmedPaymentForAccess()).isTrue();
        assertThat(result.version()).isEqualTo(1);

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                select action_code, resource_type, resource_id,
                       actor_user_id, actor_identifier_snapshot,
                       occurred_at, metadata::text as metadata,
                       metadata ->> 'previousValue' as previous_value,
                       metadata ->> 'newValue' as new_value
                from gym.audit_entries
                where action_code = 'ACCESS_PAYMENT_POLICY_CHANGED'
                """);

        assertThat(row)
                .containsEntry("action_code", "ACCESS_PAYMENT_POLICY_CHANGED")
                .containsEntry("resource_type", "SETTINGS")
                .containsEntry("resource_id", SETTINGS_RESOURCE_ID)
                .containsEntry("actor_user_id", adminId)
                .containsEntry("actor_identifier_snapshot", ADMIN_USERNAME)
                .containsEntry("previous_value", "false")
                .containsEntry("new_value", "true");
        assertThat(row.get("occurred_at")).isNotNull();
        assertThat(row.get("metadata").toString())
                .doesNotContain("payment", "stripe", "token", "card", "secret");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminNoOpDoesNotCreateAnAuditEntry() {
        var result = policyService.update(
                new UpdateAccessPaymentPolicyCommand(false, 0),
                new AccessPaymentPolicyActor(adminId, ADMIN_USERNAME));

        assertThat(result.version()).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.audit_entries
                where action_code = 'ACCESS_PAYMENT_POLICY_CHANGED'
                """, Integer.class)).isZero();
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void receptionistCannotReadTheAdministrativePolicy() {
        assertThatThrownBy(() -> policyService.findCurrent(
                new AccessPaymentPolicyActor(receptionistId, RECEPTIONIST_USERNAME)))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.audit_entries
                where action_code = 'ACCESS_PAYMENT_POLICY_CHANGED'
                """, Integer.class)).isZero();
    }

    private static final java.util.UUID SETTINGS_RESOURCE_ID =
            AccessPaymentPolicyChanged.SETTINGS_RESOURCE_ID;
}
