package io.github.guillermodubon.coachgym.payment.infrastructure.stripe;

interface StripeSdkClient {

    StripeCheckoutResult createCheckout(StripeCheckoutCreationRequest request);

    void expireCheckout(String checkoutReference);

    StripeVerifiedEvent verifyWebhook(StripeWebhookRequest request);
}
