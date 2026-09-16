package io.github.guillermodubon.coachgym.configuration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.guillermodubon.coachgym.maintenance.AbstractIncidentApiIntegrationTest;
import org.junit.jupiter.api.Test;

/** OpenAPI contract for the administrative access-payment policy routes. */
class AccessPaymentPolicyOpenApiIntegrationTest extends AbstractIncidentApiIntegrationTest {

    @Test
    void documentsSecureReadAndUpdateRoutesAndSchemas() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/settings/access-payment-policy'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/settings/access-payment-policy'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/settings/access-payment-policy'].get.security[0].sessionCookie")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/settings/access-payment-policy'].put.security[0].sessionCookie")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/settings/access-payment-policy'].put.responses['400']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/settings/access-payment-policy'].put.responses['409']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/settings/access-payment-policy'].put.description",
                        containsString("CSRF")))
                .andExpect(jsonPath("$.paths['/api/v1/settings/access-payment-policy'].put.description",
                        containsString("PAID")))
                .andExpect(jsonPath("$.paths['/api/v1/settings/access-payment-policy'].put.description",
                        containsString("PaymentAttempt")))
                .andExpect(jsonPath("$.paths['/api/v1/settings/access-payment-policy'].put.description",
                        containsString("redirects")))
                .andExpect(jsonPath("$.components.schemas.UpdateAccessPaymentPolicyRequest.properties"
                        + ".requireConfirmedPaymentForAccess").exists())
                .andExpect(jsonPath("$.components.schemas.UpdateAccessPaymentPolicyRequest.properties.version")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.UpdateAccessPaymentPolicyRequest.properties.actor")
                        .doesNotExist())
                .andExpect(jsonPath("$.components.schemas.AccessPaymentPolicyResponse.properties"
                        + ".updatedByUserId").exists())
                .andExpect(content().string(not(containsString("stripeSecretKey"))));
    }
}
