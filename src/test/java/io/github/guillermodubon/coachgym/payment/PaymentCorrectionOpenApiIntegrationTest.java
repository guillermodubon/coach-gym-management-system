package io.github.guillermodubon.coachgym.payment;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class PaymentCorrectionOpenApiIntegrationTest
        extends AbstractPaymentApiIntegrationTest {

    @Test
    void documentsVoidRefundCorrectionAndHistoryOperations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/payments/{paymentId}/void'].post")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/payments/{paymentId}/refund'].post")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/payments/{paymentId}/correction'].get")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/payments/{paymentId}/status-history'].get")
                        .exists());
    }

    @Test
    void documentsSessionSecurityAndCorrectionResponses() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/payments/{paymentId}/void'].post.security[0].sessionCookie")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/payments/{paymentId}/refund'].post.security[0].sessionCookie")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/payments/{paymentId}/void'].post.responses['409']")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/payments/{paymentId}/refund'].post.responses['409']")
                        .exists());
    }

    @Test
    void refundRequestDoesNotDocumentServerControlledFinancialFields()
            throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.components.schemas.RefundPaymentRequest.required")
                        .exists())
                .andExpect(jsonPath(
                        "$.components.schemas.RefundPaymentRequest.properties.reason")
                        .exists())
                .andExpect(jsonPath(
                        "$.components.schemas.RefundPaymentRequest.properties.version")
                        .exists())
                .andExpect(jsonPath(
                        "$.components.schemas.RefundPaymentRequest.properties.amount")
                        .doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.RefundPaymentRequest.properties.currency")
                        .doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.RefundPaymentRequest.properties.status")
                        .doesNotExist());
    }

    @Test
    void documentationDoesNotIntroduceStripeOrMaintenanceRole() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        not(containsString("ROLE_MAINTENANCE"))))
                .andExpect(content().string(
                        not(containsString("hasRole('MAINTENANCE')"))))
                .andExpect(content().string(
                        not(containsString("PaymentIntent"))))
                .andExpect(content().string(
                        not(containsString("CheckoutSession"))));
    }
}
