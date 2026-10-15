# ADR-001: Stripe Test Mode payment-attempt boundary

- Status: Accepted
- Date: 2026-09-10
- Scope: `feature/stripe-test-payments`, Block 1

## Context

Coach Gym records confirmed payments as `Payment`. A card-provider interaction
can be created, fail, expire, be cancelled, or be delivered repeatedly before
it represents confirmed money. Treating that interaction as a pending
`Payment` would make reporting and financial correction semantics ambiguous.

The product supports Stripe Checkout in Test Mode only. Stripe must host card
entry. Browser success or cancel redirects are not authoritative. A signed,
verified provider webhook is the future authority for provider confirmation.

## Decision

1. The `payment` module owns `PaymentAttempt`, its state policy, provider
   boundary, future persistence, and the decision to create a confirmed
   `Payment`.
2. `PaymentAttempt` is distinct from `Payment`. Its states are `CREATED`,
   `PROCESSING`, `SUCCEEDED`, `FAILED`, `CANCELLED`, and `EXPIRED`.
3. Only a verified successful provider event may later transition an attempt
   to `SUCCEEDED` and create exactly one `Payment PAID` with method `CARD`.
   Failed, cancelled, and expired attempts create no payment.
4. Provider-neutral application ports model checkout creation, provider-side
   cancellation, and webhook verification. Stripe SDK types remain exclusively
   inside a future infrastructure adapter.
5. Checkout amount and currency are future server-derived values from the
   membership-period snapshot. HTTP callers never supply final status,
   provider identifiers, amount, currency, or actor identity.
6. Provider calls will use short, separate local transactions. No database
   transaction remains open while calling Stripe.
7. A future webhook endpoint may have a narrow CSRF exclusion only after
   signature verification, persistent event idempotency, and replay-safe
   processing are implemented together.
8. Future persistence records only safe identifiers and outcome codes. It
   never stores card data, secrets, full webhook signatures, or raw webhook
   payloads. Public events never contain provider reference values or checkout
   URLs.
9. Stripe Test Mode is the only permitted provider mode. A future adapter must
   reject Live Mode keys and remain disabled until its external configuration
   is explicitly enabled.

## Considered alternatives

### Add a pending state to `Payment`

Rejected. `Payment` means money confirmed or manually received. A pending
provider interaction must not affect payment reporting or corrections.

### Couple application services directly to the Stripe SDK

Rejected. It would spread provider-specific types into domain/application code,
make tests depend on the SDK, and complicate a fake offline implementation.

### Accept browser redirects as confirmation

Rejected. Redirects are user-controlled navigation signals, not proof of a
confirmed provider payment.

### Store webhook payloads or signatures for debugging

Rejected. Persistent provider-event identity and safe outcome metadata are
sufficient for idempotency and audit. Raw payloads and signatures increase
privacy and secret-exposure risk without being required by v1.0.

## Consequences

- Block 2 will add forward-only PostgreSQL storage, optimistic locking,
  append-only status history, unique provider references, and persistent
  provider-event idempotency.
- Block 3 will add the Stripe Test Mode adapter and external configuration.
- Blocks 4 through 6 will add orchestration, verified webhook processing, and
  secured HTTP endpoints.
- Automated tests use only deterministic fakes or mocks at the provider port;
  they never call Stripe or any external network.
