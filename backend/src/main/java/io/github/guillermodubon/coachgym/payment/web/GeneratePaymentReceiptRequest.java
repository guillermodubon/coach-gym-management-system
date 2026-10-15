package io.github.guillermodubon.coachgym.payment.web;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.guillermodubon.coachgym.payment.application.GeneratePaymentReceiptCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Empty request contract; receipt data is always obtained from the server. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
        description = "Empty request. Receipt financial and identity fields are server controlled.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record GeneratePaymentReceiptRequest() {

    /**
     * Rejects client-controlled fields explicitly, even when the application
     * ObjectMapper is configured to ignore unknown properties globally.
     */
    @JsonAnySetter
    void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported receipt request field: " + name);
    }

    GeneratePaymentReceiptCommand toCommand(UUID paymentId) {
        return new GeneratePaymentReceiptCommand(paymentId);
    }
}
