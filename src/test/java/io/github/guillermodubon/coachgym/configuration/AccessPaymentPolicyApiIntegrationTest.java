package io.github.guillermodubon.coachgym.configuration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** HTTP security, optimistic locking, and safe error contract for policy administration. */
class AccessPaymentPolicyApiIntegrationTest extends AbstractIncidentApiIntegrationTest {

    private static final String POLICY_PATH = "/api/v1/settings/access-payment-policy";

    @BeforeEach
    void resetPolicy() {
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
    void anonymousReadAndUpdateRequireAuthentication() throws Exception {
        mockMvc.perform(get(POLICY_PATH))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put(POLICY_PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(false, 0)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void receptionistCannotReadOrUpdateAdministrativePolicy() throws Exception {
        var session = loginAsReceptionist();

        mockMvc.perform(get(POLICY_PATH).session(session))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(POLICY_PATH)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(true, 0)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadAndUpdateWithCsrfAndExpectedVersion() throws Exception {
        var session = loginAsAdmin();

        mockMvc.perform(get(POLICY_PATH).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requireConfirmedPaymentForAccess").value(false))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedByUserId").doesNotExist());

        mockMvc.perform(put(POLICY_PATH)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(true, 0)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));

        mockMvc.perform(put(POLICY_PATH)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(true, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requireConfirmedPaymentForAccess").value(true))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedByUserId").value(adminId.toString()));

        mockMvc.perform(put(POLICY_PATH)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(false, 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCESS_PAYMENT_POLICY_VERSION_CONFLICT"));
    }

    @Test
    void requestMustContainOnlyApprovedFields() throws Exception {
        mockMvc.perform(put(POLICY_PATH)
                        .session(loginAsAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requireConfirmedPaymentForAccess": false,
                                  "version": 0,
                                  "actor": "client-controlled"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void requestValidationUsesStablePolicyProblemCode() throws Exception {
        mockMvc.perform(put(POLICY_PATH)
                        .session(loginAsAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requireConfirmedPaymentForAccess\":true,\"version\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCESS_PAYMENT_POLICY_VALIDATION_FAILED"));
    }

    private static String validBody(boolean required, long version) {
        return "{\"requireConfirmedPaymentForAccess\":" + required
                + ",\"version\":" + version + "}";
    }
}
