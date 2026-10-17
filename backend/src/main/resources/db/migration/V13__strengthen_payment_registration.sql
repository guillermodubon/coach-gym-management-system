-- Enforce uniqueness of external references per payment method.
-- NULL references are excluded: two payments without a reference on the same method are valid.
-- Required by the DUPLICATE_PAYMENT_REFERENCE business rule in payment registration.
CREATE UNIQUE INDEX uq_payments_method_external_reference
    ON gym.payments (payment_method, external_reference)
    WHERE external_reference IS NOT NULL;
