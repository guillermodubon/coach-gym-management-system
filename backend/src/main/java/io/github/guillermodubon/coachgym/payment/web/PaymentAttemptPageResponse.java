package io.github.guillermodubon.coachgym.payment.web;

import io.github.guillermodubon.coachgym.payment.application.PaymentAttemptPage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Safe page envelope for payment-attempt recovery. */
@Schema(name = "PaymentAttemptPageResponse",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
        description = "A bounded page of payment attempts for one membership period.")
record PaymentAttemptPageResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<PaymentAttemptResponse> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long totalElements,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int totalPages) {

    static PaymentAttemptPageResponse from(PaymentAttemptPage page) {
        return new PaymentAttemptPageResponse(
                page.items().stream().map(PaymentAttemptResponse::from).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages());
    }
}
