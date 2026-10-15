package io.github.guillermodubon.coachgym.payment;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class PaymentAttemptOpenApiIntegrationTest extends AbstractPaymentApiIntegrationTest {

    @Test
    void documentsAllPaymentAttemptAndWebhookRoutes() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/payment-attempts/stripe-checkout'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/payment-attempts/{attemptId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/payment-attempts/{attemptId}/cancel'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/payment-provider/stripe/webhook'].post").exists());
    }

    @Test
    void documentsSecurityStatusesAndTestModeSemantics() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/payment-attempts/stripe-checkout'].post.security[0].sessionCookie")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/payment-attempts/{attemptId}'].get.security[0].sessionCookie")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/payment-attempts/{attemptId}/cancel'].post.security[0].sessionCookie")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/payment-provider/stripe/webhook'].post.security[0].stripeWebhookSignature")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/payment-attempts/stripe-checkout'].post.responses['201']")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/payment-provider/stripe/webhook'].post.responses['204']")
                        .exists())
                .andExpect(jsonPath(
                        "$.components.schemas.StripeCheckoutResponse.properties.sandbox")
                        .exists());
    }

    @Test
    void checkoutRequestExcludesProviderAndFinancialFields() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.CreateStripeCheckoutRequest.properties.clientId").exists())
                .andExpect(jsonPath("$.components.schemas.CreateStripeCheckoutRequest.properties.membershipId").exists())
                .andExpect(jsonPath("$.components.schemas.CreateStripeCheckoutRequest.properties.membershipPeriodId").exists())
                .andExpect(jsonPath("$.components.schemas.CreateStripeCheckoutRequest.properties.amount").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CreateStripeCheckoutRequest.properties.currency").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CreateStripeCheckoutRequest.properties.status").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CreateStripeCheckoutRequest.properties.provider").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CreateStripeCheckoutRequest.properties.actor").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/payment-attempts/{attemptId}/success'].post").doesNotExist())
                .andExpect(content().string(not(containsString("PaymentIntent"))))
                .andExpect(content().string(not(containsString("CheckoutSession"))));
    }
}
