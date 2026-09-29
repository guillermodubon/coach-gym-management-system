package io.github.guillermodubon.coachgym.reporting.web;

import io.github.guillermodubon.coachgym.payment.PaidPaymentTrendPoint;
import io.github.guillermodubon.coachgym.reporting.PaidFinancialTrendReport;
import io.github.guillermodubon.coachgym.reporting.ReportingGranularity;
import java.util.List;

/** Bounded financial trend response with currency separated per point. */
public record FinancialTrendResponse(
        ReportingApiContextResponse context,
        ReportingGranularity granularity,
        List<PaidPaymentTrendPoint> points) {

    public FinancialTrendResponse {
        points = List.copyOf(points);
    }

    static FinancialTrendResponse from(PaidFinancialTrendReport report) {
        return new FinancialTrendResponse(
                ReportingApiContextResponse.from(report.context()),
                report.granularity(),
                report.points());
    }
}
