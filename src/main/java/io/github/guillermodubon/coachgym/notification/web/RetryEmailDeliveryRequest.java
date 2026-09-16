package io.github.guillermodubon.coachgym.notification.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.guillermodubon.coachgym.notification.application.RetryEmailDeliveryCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Version-only retry request; the server owns the recipient and attachment. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
        description = "Explicit optimistic-lock version for a failed delivery retry.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record RetryEmailDeliveryRequest(
        @Schema(description = "Current delivery version read by the caller.", example = "1")
        long expectedVersion) {

    @JsonAnySetter
    void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported email retry field: " + name);
    }

    RetryEmailDeliveryCommand toCommand(UUID deliveryId) {
        return new RetryEmailDeliveryCommand(deliveryId, expectedVersion);
    }
}
