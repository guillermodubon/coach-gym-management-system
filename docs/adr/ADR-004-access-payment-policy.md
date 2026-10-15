# ADR-004: Confirmed-payment requirement for gym access

**Status:** Accepted

**Date:** 2026-09-14

**Scope:** `feature/access-payment-policy`, Block 1

## Context

Coach Gym already evaluates identity, client status, membership state, the
authoritative membership period, and QR duplicate scans through the access
module. The product blueprint allows the gym to require a confirmed payment
before granting access. That rule must not make payment-provider state or
financial details part of the access contract.

The current settings table is the authoritative home for global gym settings
and already provides optimistic versioning and actor/timestamp columns. The
policy will be represented as a typed projection of that persisted setting in
a later block. This block establishes the policy and query contracts only.

## Decision

1. **Authoritative setting.** `requireConfirmedPaymentForAccess` is a
   PostgreSQL-backed gym setting. Environment variables, static constants, and
   provider configuration are not authoritative business state.
2. **Default and compatibility.** The initial value is `false`. This preserves
   the behavior of all existing manual and QR check-ins when the setting is
   introduced. An administrator may enable it through the later settings
   workflow.
3. **Ownership.** The configuration/settings module owns the value and its
   administrative lifecycle. Only an authenticated `ADMIN` may change it;
   `RECEPTIONIST` has no administrative update permission by default.
4. **Qualifying payment.** Access is satisfied only by at least one persisted
   `Payment` whose status is exactly `PAID` and whose client, membership, and
   membership-period identifiers equal the tuple selected by the access
   workflow. Existing payment-registration rules remain authoritative for
   amount and currency. `VOIDED` and `REFUNDED` payments never qualify.
5. **No inferred or external payment state.** Payment attempts, checkout
   sessions, redirects, provider events, external references, and Stripe API
   calls do not satisfy access. PostgreSQL is the source of truth and the
   exposed payment contract returns only the existence decision.
6. **Exact period.** The requirement is evaluated for the current membership
   period already resolved by the access workflow. It never searches an
   arbitrary client payment, another period, a future period, or a previous
   paid period, and it never accepts a period supplied by the request.
7. **One shared decision path.** Manual client-code, manual membership-code,
   and QR check-in must eventually pass through the same access policy. The
   payment requirement is applied only after identity, client, membership,
   state, date, and period rules have produced an otherwise eligible result.
   Existing QR duplicate-scan behavior remains an explicit part of that path.
8. **Precedence and privacy.** An earlier non-financial denial is returned
   unchanged and does not become `PAYMENT_REQUIRED`. When the policy is
   enabled and an otherwise eligible request has no qualifying payment, the
   normal denied access result uses `PAYMENT_REQUIRED`. Responses and audit
   records expose only the safe reason code; they do not expose amounts,
   methods, provider identifiers, payment attempts, or payment existence
   details beyond the decision itself.
9. **Query failures.** An unexpected failure while reading confirmed payment
   state is a safe system failure, never a business `PAYMENT_REQUIRED` denial
   and never a fail-open access grant. The later application adapter maps the
   failure without leaking SQL, provider, or infrastructure details.
10. **Audit and reporting.** Later integration reuses the existing access
    attempt and audit events. Reporting may count `PAYMENT_REQUIRED` among
    denial reasons, but it must not expose payment internals or derive access
    from payment-attempt data.

## Alternatives considered

- **Environment-only feature flag:** rejected because business policy must be
  persisted, versioned, and auditable.
- **Checking the latest payment or any client payment:** rejected because it
  can authorize the wrong membership period and disclose or apply stale
  financial state.
- **Calling Stripe during check-in:** rejected because provider redirects and
  network availability cannot establish authoritative access state.
- **Separate manual and QR payment checks:** rejected because duplicated
  policy would drift and produce inconsistent access decisions.

## Consequences

- The configuration module exposes an immutable `AccessPaymentPolicy` and a
  versioned update command without accepting actor or timestamp values from a
  request.
- The payment module exposes a minimal, technology-neutral existence query
  keyed by the exact client/membership/period tuple.
- Access adds the safe `PAYMENT_REQUIRED` reason and a pure evaluator contract;
  persistence, adapters, HTTP, audit, reporting, and Flyway work are deferred
  to the authorized later blocks.

## Non-goals for this block

- No Flyway migration or settings persistence adapter.
- No payment query implementation or Stripe integration.
- No access application-service wiring, events, audit, reporting, controller,
  OpenAPI, membership, promotion, refund, or frontend changes.
