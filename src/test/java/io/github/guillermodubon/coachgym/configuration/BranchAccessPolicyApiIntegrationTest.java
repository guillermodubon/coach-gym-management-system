package io.github.guillermodubon.coachgym.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class BranchAccessPolicyApiIntegrationTest extends AbstractIncidentApiIntegrationTest {

    private static final UUID INITIAL_BRANCH_ID = UUID.fromString(
            "7b0bf7d5-5184-43d2-8f9a-200000000002");

    @BeforeEach
    void resetPolicyApiFixture() {
        jdbcTemplate.update("""
                update gym.gym_settings
                set require_confirmed_payment_for_access = false,
                    updated_by_user_id = null,
                    version = 0
                where id = 1
                """);
        jdbcTemplate.update("""
                insert into gym.branch_access_policy_overrides
                    (branch_id, policy_mode, updated_by_user_id, version)
                values (?, 'INHERIT', null, 0)
                on conflict (branch_id) do update set
                    policy_mode = 'INHERIT',
                    updated_by_user_id = null,
                    version = 0
                """, INITIAL_BRANCH_ID);
        jdbcTemplate.update("""
                delete from gym.audit_entries
                where action_code = 'BRANCH_ACCESS_PAYMENT_POLICY_CHANGED'
                  and resource_id = ?
                """, INITIAL_BRANCH_ID);
    }

    @Test
    void policyReadsAreBranchScopedAndMutationsAreAdminOnlyVersionedAndAudited()
            throws Exception {
        MockHttpSession receptionist = loginAsReceptionist();
        mockMvc.perform(get("/api/v1/branches/{id}/access-payment-policy", INITIAL_BRANCH_ID))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/branches/{id}/access-payment-policy", INITIAL_BRANCH_ID)
                        .session(receptionist))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branchId").value(INITIAL_BRANCH_ID.toString()))
                .andExpect(jsonPath("$.branchMode").value("INHERIT"))
                .andExpect(jsonPath("$.requireConfirmedPaymentForAccess").value(false))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.organizationDefaultRequiresConfirmedPayment").doesNotExist());

        UUID unassignedBranchId = UUID.randomUUID();
        var deniedRead = mockMvc.perform(get(
                        "/api/v1/branches/{id}/access-payment-policy", unassignedBranchId)
                        .session(receptionist))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("BRANCH_ACCESS_POLICY_FORBIDDEN"))
                .andExpect(jsonPath("$.detail").value(
                        "The authenticated staff member is not authorized for this branch policy operation."))
                .andReturn();
        assertThat(deniedRead.getResponse().getContentAsString())
                .doesNotContain("organizationId");

        mockMvc.perform(put(
                        "/api/v1/branches/{id}/access-payment-policy", INITIAL_BRANCH_ID)
                        .with(csrf())
                        .session(receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"REQUIRED\",\"expectedVersion\":0}"))
                .andExpect(status().isForbidden());

        MockHttpSession admin = loginAsAdmin();
        String required = "{\"mode\":\"REQUIRED\",\"expectedVersion\":0}";
        mockMvc.perform(put(
                        "/api/v1/branches/{id}/access-payment-policy", INITIAL_BRANCH_ID)
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(required))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));

        mockMvc.perform(put(
                        "/api/v1/branches/{id}/access-payment-policy", INITIAL_BRANCH_ID)
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(required))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branchMode").value("REQUIRED"))
                .andExpect(jsonPath("$.requireConfirmedPaymentForAccess").value(true))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(put(
                        "/api/v1/branches/{id}/access-payment-policy", INITIAL_BRANCH_ID)
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(required))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("BRANCH_ACCESS_POLICY_VERSION_CONFLICT"))
                .andExpect(jsonPath("$.detail").value(
                        "The branch access-payment policy was modified by another operation."));

        mockMvc.perform(get("/api/v1/branches/{id}/access-payment-policy", INITIAL_BRANCH_ID)
                        .session(receptionist))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branchMode").value("REQUIRED"))
                .andExpect(jsonPath("$.requireConfirmedPaymentForAccess").value(true));

        mockMvc.perform(delete(
                        "/api/v1/branches/{id}/access-payment-policy", INITIAL_BRANCH_ID)
                        .with(csrf())
                        .session(admin)
                        .param("expectedVersion", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branchMode").value("INHERIT"))
                .andExpect(jsonPath("$.requireConfirmedPaymentForAccess").value(false))
                .andExpect(jsonPath("$.version").value(2));

        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from gym.audit_entries
                where action_code = 'BRANCH_ACCESS_PAYMENT_POLICY_CHANGED'
                  and resource_id = ?
                """, Integer.class, INITIAL_BRANCH_ID)).isEqualTo(2);
        String metadata = jdbcTemplate.queryForObject("""
                select string_agg(metadata::text, ' ')
                from gym.audit_entries
                where action_code = 'BRANCH_ACCESS_PAYMENT_POLICY_CHANGED'
                  and resource_id = ?
                """, String.class, INITIAL_BRANCH_ID);
        assertThat(metadata)
                .contains("previousMode", "newMode", "version")
                .doesNotContain("payment details", "card", "token", "secret");
    }
}
