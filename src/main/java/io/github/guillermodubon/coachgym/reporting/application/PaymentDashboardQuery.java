package io.github.guillermodubon.coachgym.reporting.application;

import io.github.guillermodubon.coachgym.reporting.PaymentDashboardDetails;

/** Read port for effective paid revenue within the requested period. */
public interface PaymentDashboardQuery {

    PaymentDashboardDetails summarize(
            DashboardPeriod period,
            String expectedCurrency);
}
