package io.github.guillermodubon.coachgym.reporting.web;

import io.github.guillermodubon.coachgym.reporting.PaymentDashboardDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record PaymentDashboardResponse(
        long paidCount,
        @Schema(minimum = "0.00", example = "4250.00") BigDecimal registeredAmount,
        @Schema(pattern = "[A-Z]{3}", example = "USD") String currency) {
    static PaymentDashboardResponse from(PaymentDashboardDetails details) {
        return new PaymentDashboardResponse(
                details.paidCount(), details.registeredAmount(), details.currency());
    }
}
