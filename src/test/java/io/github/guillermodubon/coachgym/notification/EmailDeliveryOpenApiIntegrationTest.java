package io.github.guillermodubon.coachgym.notification;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** OpenAPI contract for staff transactional-email delivery operations. */
class EmailDeliveryOpenApiIntegrationTest extends AbstractNotificationApiIntegrationTest {

    @Test
    void documentsRequestsHistoryAttemptsAndRetry() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/email-deliveries/payment-receipts/{paymentId}'].post")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/email-deliveries/access-credentials/{clientId}'].post")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/email-deliveries'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/email-deliveries/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/email-deliveries/{id}/attempts'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/email-deliveries/{id}/retry'].post").exists());
    }

    @Test
    void documentsSessionSecurityFiltersAndSafeSchemas() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/email-deliveries'].get.parameters[*].name")
                        .value(containsInAnyOrder(
                                "deliveryType", "status", "clientId", "sourceResourceId",
                                "requestedFrom", "requestedUntil", "page", "size", "sort", "direction",
                                "branchId")))
                .andExpect(jsonPath("$.paths['/api/v1/email-deliveries'].get.security[0].sessionCookie")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/email-deliveries/{id}/retry'].post.security[0].sessionCookie")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.EmailDeliveryResponse.properties.maskedRecipient")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.EmailDeliveryResponse.properties.branchId")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.EmailDeliveryResponse.properties.recipientSnapshot")
                        .doesNotExist())
                .andExpect(jsonPath("$.components.schemas.EmailDeliveryAttemptResponse.properties.providerMessageId")
                        .doesNotExist())
                .andExpect(content().string(not(containsString("ROLE_MAINTENANCE"))))
                .andExpect(content().string(containsString("not guaranteed exactly once")));
    }
}
