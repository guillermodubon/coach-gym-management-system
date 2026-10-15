package io.github.guillermodubon.coachgym.payment;

import io.github.guillermodubon.coachgym.shared.ReportingQueryScope;
import io.github.guillermodubon.coachgym.shared.ReportingQueryWindow;
import java.util.List;

/** Source-owned read port for bounded, branch-filtered payment aggregates. */
public interface PaymentReportingQuery {

    PaymentFinancialMetrics summarize(
            ReportingQueryScope scope,
            ReportingQueryWindow window);

    List<PaymentMethodDistribution> paymentMethodDistribution(
            ReportingQueryScope scope,
            ReportingQueryWindow window);

    /** Missing bucket rows mean that the bucket has no qualifying paid payments. */
    List<PaidPaymentTrendPoint> paidTrend(
            ReportingQueryScope scope,
            ReportingQueryWindow window,
            PaymentTrendGranularity granularity);
}
