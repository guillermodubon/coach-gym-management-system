package io.github.guillermodubon.coachgym.reporting;

import io.github.guillermodubon.coachgym.payment.PaidPaymentTrendPoint;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Safe bounded paid-payment trend with currency kept separate. */
public record PaidFinancialTrendReport(
        ReportingContext context,
        ReportingGranularity granularity,
        List<PaidPaymentTrendPoint> points) {

    public PaidFinancialTrendReport {
        Objects.requireNonNull(context, "Reporting context is required.");
        Objects.requireNonNull(granularity, "Reporting granularity is required.");
        Objects.requireNonNull(points, "Payment trend points are required.");
        if (points.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Payment trend points are invalid.");
        }
        points = points.stream()
                .sorted(Comparator.comparing(PaidPaymentTrendPoint::bucketStart)
                        .thenComparing(PaidPaymentTrendPoint::currency))
                .toList();
    }
}
