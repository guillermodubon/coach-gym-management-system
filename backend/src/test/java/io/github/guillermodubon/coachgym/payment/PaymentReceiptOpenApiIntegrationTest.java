package io.github.guillermodubon.coachgym.payment;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class PaymentReceiptOpenApiIntegrationTest extends AbstractPaymentApiIntegrationTest {

    @Test
    void documentsReceiptMetadataGenerationAndPdfRoutes() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/payments/{paymentId}/receipt'].post")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/payments/{paymentId}/receipt'].get")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/payments/{paymentId}/receipt.pdf'].get")
                        .exists());
    }

    @Test
    void documentsSessionSecurityStatusesAndPdfRepresentation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/payments/{paymentId}/receipt'].post.security[0].sessionCookie")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/payments/{paymentId}/receipt'].get.security[0].sessionCookie")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/payments/{paymentId}/receipt.pdf'].get.security[0].sessionCookie")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/payments/{paymentId}/receipt'].post.responses['201']")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/payments/{paymentId}/receipt'].get.responses['200']")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/payments/{paymentId}/receipt.pdf'].get.responses['200'].content['application/pdf'].schema.format")
                        .value("binary"));
    }

    @Test
    void receiptRequestDocumentsNoClientControlledFinancialOrProviderFields()
            throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.components.schemas.GeneratePaymentReceiptRequest")
                        .exists())
                .andExpect(jsonPath(
                        "$.components.schemas.GeneratePaymentReceiptRequest.properties.amount")
                        .doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.GeneratePaymentReceiptRequest.properties.currency")
                        .doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.GeneratePaymentReceiptRequest.properties.receiptNumber")
                        .doesNotExist())
                .andExpect(content().string(not(containsString("PaymentIntent"))))
                .andExpect(content().string(not(containsString("CheckoutSession"))));
    }
}
