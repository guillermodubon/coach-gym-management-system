package io.github.guillermodubon.coachgym.payment.application;

import io.github.guillermodubon.coachgym.payment.PaymentReceiptOrganization;

/** Read port for the singleton authoritative organization settings. */
public interface PaymentReceiptOrganizationQuery {

    PaymentReceiptOrganization findCurrent();
}
