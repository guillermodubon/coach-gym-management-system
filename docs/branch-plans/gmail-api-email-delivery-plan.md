# Branch Plan: `feature/gmail-api-email-delivery`

## 1. Branch identity

- **Branch:** `feature/gmail-api-email-delivery`
- **Capability:** Replace provisional SMTP, Mailpit, and Resend transports with one secure Gmail API delivery adapter over HTTPS and OAuth 2.0
- **Previous completed branch:** `feature/staff-invitation-account-provisioning`
- **Expected next branch:** `feature/multi-branch-reporting-audit`
- **Current migration baseline:** Verify from repository, expected through `V42`
- **Starting progress:** 0%
- **Target progress:** 100%
- **Execution model:** One explicitly authorized block per Codex response
- **Plan owner:** User
- **Implementation agent:** Codex
- **Git ownership:** The user controls staging, commits, pushes, rebases, pull requests, merges, and branch changes

## 2. Purpose

Replace the current provisional email transports with a single production-shaped transport based on Gmail API.

The existing transactional-email domain must remain intact:

- provider-neutral `EmailSender` boundary;
- durable delivery records;
- append-only attempts;
- `PENDING`, `SENT`, and `FAILED` states;
- PostgreSQL idempotency;
- claim leases and concurrency control;
- optimistic locking;
- bounded retries;
- canonical PDF and PNG attachments;
- HTML and plain-text templates;
- audit;
- metrics;
- API and OpenAPI contracts;
- branch ownership and authorization;
- identity-related emails added by staff invitation and account provisioning.

This branch changes the transport and transport-specific configuration. It must not rebuild the email-delivery domain.

The final transport architecture is:

```text
TransactionalEmailDeliveryApplicationService
                    ↓
                EmailSender
                    ↓
          GmailApiEmailSenderAdapter
                    ↓ HTTPS + OAuth 2.0
                Gmail API
```

## 3. Product and deployment context

The final deferred deployment target remains:

```text
Frontend: Vercel Hobby
Backend: Render Free
Database: Supabase PostgreSQL Free
Files: Supabase Storage Free
Email: Gmail API over HTTPS
Payments: Stripe Test Mode
```

Gmail API is selected because:

- it operates over HTTPS;
- it does not require SMTP connectivity from Render;
- it does not require a purchased domain;
- it permits real delivery from a dedicated Gmail account;
- it keeps the project budget at or near $0 for low portfolio traffic;
- it allows recruiters to receive real emails;
- it preserves the existing provider-neutral architecture.

The sender account must be dedicated to the project, not the developer's primary personal account.

Example deployment sender:

```text
Coach Gym Demo <coachgym.demo@gmail.com>
```

## 4. Execution protocol for Codex

This plan provides complete context but authorizes only the block explicitly requested in the current prompt.

For every block, Codex must:

1. Read the root and applicable nested `AGENTS.md` files.
2. Read this complete branch plan.
3. Read the current blueprint.
4. Inspect the current repository and `git status --short` before editing.
5. Inspect existing email domain, persistence, providers, configuration, tests, security, audit, metrics, Docker Compose, OpenAPI, and documentation.
6. Preserve unrelated changes.
7. Implement only the authorized block.
8. Run focused validation.
9. Fix failures introduced by the block.
10. Perform a secondary consistency check.
11. Report evidence and stop.

Codex must not:

- implement later blocks early;
- stage, commit, push, rebase, merge, switch branches, or open a pull request;
- modify an applied Flyway migration;
- execute Flyway repair automatically;
- redesign the transactional-email domain;
- bypass `EmailSender`;
- send real emails from standard automated tests;
- require Gmail secrets for `clean check`;
- log OAuth tokens, message bodies, attachment bytes, or MIME payloads;
- place Gmail-specific types in public/application contracts;
- claim final recipient delivery when Gmail only accepted the message for sending;
- add frontend code;
- deploy external infrastructure;
- create temporary patch scripts or generated artifacts in the repository.

## 5. Goals

1. Make Gmail API the only real email transport.
2. Remove Mailpit, SMTP, and Resend transport implementations and configuration.
3. Preserve all current email business behavior and public API contracts where possible.
4. Implement secure OAuth 2.0 refresh-token handling.
5. Build valid MIME messages containing text, HTML, PDF, and PNG attachments.
6. Encode Gmail raw messages using URL-safe Base64.
7. Map Gmail and OAuth failures to stable safe application outcomes.
8. Keep standard tests deterministic and independent of Internet access.
9. Add an optional real Gmail integration test task.
10. Validate every current email type.
11. Protect project Gmail credentials and prevent email abuse.
12. Update health, metrics, OpenAPI, configuration, Compose, and documentation.
13. Leave the backend ready for multisucursal reporting and final hardening.

## 6. Non-goals

This branch must not implement:

- SMTP fallback;
- Mailpit fallback;
- Resend fallback;
- multiple selectable production providers;
- Gmail inbox reading;
- inbound email processing;
- delivery/open/click tracking;
- bounce webhooks unavailable through this selected flow;
- marketing or bulk email;
- newsletters;
- client self-service;
- arbitrary email composition;
- arbitrary recipients supplied to normal business-delivery endpoints;
- public anonymous email relay;
- new business email types unrelated to the existing catalog;
- multi-factor authentication;
- frontend email screens;
- cloud deployment;
- a purchased domain;
- Google Workspace administration;
- service-account domain-wide delegation.

## 7. Required repository inspection before Block 1

Codex must inspect and confirm:

- `EmailSender` and all provider-neutral models;
- `TransactionalEmailDeliveryApplicationService`;
- `NotificationDeliveryService` or current equivalent;
- delivery status and attempt status;
- `email_deliveries` and `email_delivery_attempts` schema;
- claim/lease behavior and migration history;
- idempotency-key derivation;
- retry and ambiguous-outcome behavior;
- email composers, templates, and current package location;
- receipt PDF and credential PNG source resolvers;
- identity-email source resolvers and templates;
- SMTP sender, properties, tests, health checks, and Compose service;
- Mailpit references in code, tests, configuration, Compose, and docs;
- Resend sender, properties, tests, health checks, and docs;
- current provider selection/configuration mechanism;
- HTTP-client conventions and timeout configuration;
- secret-redaction and configuration validation patterns;
- audit events, metrics, and OpenAPI;
- current branch ownership in delivery records;
- current email abuse/rate limits;
- all email-related test suites;
- current Flyway history, expected through `V42` but requiring verification;
- Spring Modulith exposed packages.

The discovery must classify each existing email component as:

- retain unchanged;
- move to a provider-neutral package;
- adapt for Gmail;
- delete after replacement;
- update for documentation/configuration only.

## 8. Existing work that must be preserved

Do not rewrite or remove these concepts unless repository evidence proves they are obsolete:

```text
EmailSender
EmailMessage
EmailAttachment
EmailSendResult
ComposedEmail
EmailComposer or template composers
TransactionalEmailDeliveryApplicationService
EmailDeliveryStore
EmailDeliveryQuery
email_deliveries
email_delivery_attempts
claim leases
logical idempotency
optimistic locking
manual retry
safe failure codes
audit events
metrics
payment receipt source resolution
access credential source resolution
staff identity email flows
branch ownership and authorization
```

## 9. Email catalog that must remain functional

The branch must validate every email type currently approved by the repository and blueprint.

Expected catalog:

```text
PAYMENT_RECEIPT
ACCESS_CREDENTIAL
STAFF_INVITATION
STAFF_ACCOUNT_ACTIVATED
STAFF_PASSWORD_RECOVERY
STAFF_PASSWORD_CHANGED
STAFF_ACCOUNT_SUSPENDED
STAFF_ACCOUNT_REACTIVATED
STAFF_ACCOUNT_DEACTIVATED
STAFF_ROLE_SCOPE_CHANGED
STAFF_BRANCH_ASSIGNMENTS_CHANGED
```

Codex must inspect actual names rather than invent duplicates.

This is a catalog of business email flows, not an expansion of
`EmailDeliveryType`. Under accepted ADR-011, only `PAYMENT_RECEIPT` and
`ACCESS_CREDENTIAL` use the generic durable transactional delivery workflow.
All staff invitation, activation, password-recovery, and account-security
messages retain their provider-neutral ephemeral identity-email contracts and
must not create a generic durable email-outbox record or an
`email_deliveries`/`email_delivery_attempts` row.

For each type, verify:

- authoritative recipient source;
- safe subject;
- text body;
- HTML body;
- attachment behavior;
- token-link confidentiality where applicable;
- idempotency scope;
- audit behavior;
- branch ownership where applicable.

## 10. Delivery-state semantics

Retain the current states unless repository evidence requires a migration:

```text
PENDING
SENT
FAILED
```

Precise meaning:

### `PENDING`

Coach Gym has durable delivery intent but has not finalized a provider outcome.

### `SENT`

Gmail API accepted the message for sending and returned a successful message identifier.

`SENT` does **not** guarantee final inbox placement.

### `FAILED`

Coach Gym could not complete the Gmail API request or received a definitive provider rejection.

The UI, API, audit, and OpenAPI must use language equivalent to “sent” or “accepted for sending,” not “delivered to inbox.”

## 11. Ambiguous-outcome policy

There is an unavoidable failure window:

```text
Gmail accepts the message
→ backend loses the response or fails before persisting SENT
```

The branch must preserve or introduce a safe representation of uncertain outcome.

Preferred options, in order:

1. Reuse an existing safe failure code such as `EMAIL_OUTCOME_UNCERTAIN` while leaving delivery `FAILED` and requiring explicit operator decision.
2. Add a separate state only if the current domain and migration cost justify it.

Rules:

- do not automatically retry an uncertain outcome;
- do not claim the message definitely failed;
- expose only a safe operational code;
- preserve the attempt;
- require explicit retry with duplicate-risk warning if the API supports it;
- document the guarantee honestly.

## 12. OAuth 2.0 strategy

The backend uses a pre-authorized dedicated Gmail account.

Required configuration:

```text
EMAIL_ENABLED
GMAIL_API_BASE_URL
GOOGLE_OAUTH_TOKEN_URL
GMAIL_CLIENT_ID
GMAIL_CLIENT_SECRET
GMAIL_REFRESH_TOKEN
GMAIL_SENDER_ADDRESS
EMAIL_FROM_NAME
GMAIL_CONNECT_TIMEOUT
GMAIL_READ_TIMEOUT
GMAIL_WRITE_TIMEOUT
```

Exact property names must follow repository conventions.

### OAuth flow

1. Read refresh token from secret configuration.
2. Request an access token from the Google OAuth token endpoint.
3. Cache the access token in memory only until shortly before expiration.
4. Refresh under concurrency-safe single-flight behavior.
5. Send Gmail API requests with `Authorization: Bearer <token>`.
6. Retry one time after an authentication failure only when safe and after forced token refresh.
7. Never persist access tokens.
8. Never log client secret, refresh token, access token, or full OAuth response.

### Refresh-token storage

- Render secret environment variable in deployment;
- local untracked environment variable for manual testing;
- never Git;
- never Flyway;
- never normal database table;
- never OpenAPI;
- never test fixture committed with real value.

## 13. OAuth concurrency

Multiple email sends may request a token concurrently.

Requirements:

- no token-refresh stampede;
- one refresh operation when current token is absent/expired;
- waiting callers reuse fresh token;
- bounded waiting;
- refresh failure propagates safely;
- no process-local token is treated as durable business state;
- multiple backend instances may each maintain their own temporary access-token cache safely;
- refresh token remains external secret;
- tests cover concurrent refresh.

## 14. Gmail API send contract

The adapter sends through Gmail API over HTTPS.

Expected operation conceptually:

```text
POST /gmail/v1/users/me/messages/send
```

Payload contains:

```json
{
  "raw": "<base64url MIME message>"
}
```

The adapter must:

- use the dedicated sender identity;
- construct MIME completely before HTTP call;
- encode URL-safe Base64 without line-wrapping mistakes;
- validate message size before send;
- use strict timeouts;
- parse successful message identifier safely;
- map invalid/malformed responses;
- avoid logging request payload because it contains body and attachment data.

## 15. MIME requirements

Every message must support:

- From;
- To;
- Subject;
- Date;
- Message-ID or application correlation header where appropriate;
- plain-text alternative;
- HTML alternative;
- UTF-8;
- one canonical attachment for current receipt/credential flows;
- no attachment for normal identity emails unless explicitly approved.

Recommended MIME shape with attachment:

```text
multipart/mixed
└── multipart/alternative
    ├── text/plain; charset=UTF-8
    └── text/html; charset=UTF-8
└── attachment
```

Requirements:

- CRLF-compliant headers/body;
- encoded non-ASCII subjects/display names;
- header-injection prevention;
- safe attachment filenames;
- correct transfer encoding;
- correct MIME type;
- bounded message size;
- no Bcc unless explicitly required;
- no arbitrary reply-to from user input;
- no token printed outside approved activation/recovery links.

## 16. Attachment requirements

Current approved attachments:

```text
Payment receipt: application/pdf
Access credential: image/png
```

Rules:

- use canonical stored bytes;
- do not regenerate during email delivery;
- verify metadata/checksum/size where current flow supports it;
- bounded total message size;
- safe server-generated filename;
- no storage key/path in message;
- no arbitrary attachment request;
- no attachment bytes in logs, audit, events, or API responses;
- Base64 encoding verified against original bytes in tests.

Recommended internal maximum total attachment bytes should be conservative and configuration-driven. Reuse an existing validated limit where possible.

## 17. Error mapping

Map provider and OAuth outcomes to stable internal failure codes.

Recommended concepts, adjusted to current conventions:

```text
GMAIL_OAUTH_AUTHENTICATION_FAILED
GMAIL_OAUTH_RATE_LIMITED
GMAIL_OAUTH_UNAVAILABLE
GMAIL_REQUEST_INVALID
GMAIL_PERMISSION_DENIED
GMAIL_RATE_LIMITED
GMAIL_TRANSPORT_TIMEOUT
GMAIL_SERVICE_UNAVAILABLE
GMAIL_RESPONSE_INVALID
GMAIL_OUTCOME_UNCERTAIN
EMAIL_MESSAGE_TOO_LARGE
EMAIL_RECIPIENT_INVALID
EMAIL_CONFIGURATION_INVALID
```

### Retryable candidates

- connection timeout before request is sent;
- `429`;
- `500`;
- `502`;
- `503`;
- `504`.

Retry remains controlled by the existing delivery workflow. The adapter must not introduce unbounded internal retries.

### Non-retryable candidates

- invalid request/MIME;
- invalid recipient;
- invalid OAuth configuration;
- permission denied;
- message too large;
- unsupported sender.

### Authentication retry

A single forced token refresh and one resend attempt may be allowed for an expired/invalid access token only when the first Gmail request was definitively rejected before acceptance.

## 18. Provider message identifier

If Gmail returns a message identifier, store it only when the current attempt model already supports a safe external/provider identifier or a justified migration is approved.

Rules:

- not required for business correctness;
- no provider thread/message content retrieval;
- bounded string;
- not used as authorization;
- safe in operational detail only if approved;
- no high-cardinality metric tag.

Avoid a migration if the identifier provides no meaningful product or support value.

## 19. Configuration validation

Typed configuration must fail fast when email is enabled and required values are missing or invalid.

Validate:

- base URLs use HTTPS outside test/local stub profiles;
- sender address is valid;
- sender matches the authorized Gmail account policy;
- client ID/secret/refresh token nonblank when enabled;
- positive bounded timeouts;
- message-size limit positive and bounded;
- live-test flag off by default;
- no secret in properties `toString`;
- email disabled mode behaves intentionally.

The application should still start without Gmail secrets in standard automated tests.

## 20. Testing strategy

One real provider does not mean every test calls the real provider.

### Standard tests

Use the real production `GmailApiEmailSenderAdapter` against a local HTTP stub server.

Standard tests must not require:

- Internet;
- Gmail account;
- Google Cloud project;
- OAuth credentials;
- quota;
- real recipient.

### Optional live test

Provide a separate opt-in task, such as:

```powershell
.\gradlew gmailIntegrationTest
```

It must not run under:

```powershell
.\gradlew clean check
```

The live task requires explicit environment variables and sends only synthetic messages to an allowlisted test recipient.

## 21. HTTP-stub test requirements

Tests must cover both OAuth and Gmail endpoints.

### OAuth cases

- successful token refresh;
- expires-in parsing;
- malformed response;
- `400` invalid grant;
- `401`/`403`;
- `429`;
- timeout;
- `5xx`;
- concurrent single-flight refresh;
- secret redaction.

### Gmail cases

- successful send;
- returned message ID;
- malformed response;
- `400`;
- `401` followed by safe refresh/retry;
- `403`;
- `429`;
- timeout before send;
- ambiguous timeout after request body transmission where testable;
- `5xx`;
- message-size rejection;
- token/body/attachment redaction.

### MIME cases

- plain text;
- HTML;
- UTF-8/accented text;
- receipt PDF;
- credential PNG;
- activation link;
- recovery link;
- long safe subject;
- header-injection attempt;
- Base64 URL-safe decoding back to valid MIME;
- exact attachment bytes after decoding.

## 22. Live integration-test safeguards

The optional real Gmail integration test must:

- be disabled by default;
- fail with a clear skipped/disabled result when not enabled;
- require explicit `GMAIL_LIVE_TESTS_ENABLED=true`;
- require an allowlisted recipient;
- use synthetic client/resource data;
- avoid real customer information;
- send a bounded number of messages;
- use a distinctive test subject;
- never print secrets;
- verify API acceptance, not inbox delivery;
- document manual inbox verification separately;
- avoid running in pull requests or forks by default.

## 23. Anti-abuse and recruiter demo

Normal business email endpoints continue to derive recipient from authoritative client or staff data.

Do not add a generic endpoint accepting arbitrary recipient and arbitrary content.

For eventual recruiter demonstration:

- use a dedicated demo account;
- use demo clients/resources only;
- permit email change only through approved client-profile behavior;
- enforce current authentication and CSRF;
- apply existing email delivery limits;
- add or retain cooldown and per-session/per-recipient/global limits where current demo design requires them;
- preserve idempotency;
- show `SENT` as Gmail acceptance;
- never claim inbox delivery.

A separate recipient-verification feature is not required by this branch unless already approved in the blueprint.

## 24. Identity-token email confidentiality

Invitation and password-recovery emails contain one-time links.

Requirements:

- tokenized links exist only in composed email content and immediate in-memory command boundaries;
- no token in delivery-list/detail responses;
- no token in audit metadata;
- no token in event payloads broader than the narrow email composition boundary;
- no request/response body logging;
- no token in metrics;
- no tokenized URL in provider error messages persisted by the application;
- Gmail raw payload never logged;
- a generic transport retry never reuses an invitation or recovery message;
- identity-owned resend revokes or invalidates the prior token, creates a new
  token, composes a new email, and sends it through `EmailSender`.

If a token expires or is revoked, resending the same old email must not create a valid security path.

## 25. Health and readiness

Replace current SMTP/Mailpit/Resend health behavior with Gmail-specific configuration readiness.

Requirements:

- readiness checks configuration validity without sending an email;
- do not call OAuth/Gmail on every health probe;
- never expose secret values;
- distinguish disabled, configured, and invalid configuration;
- Gmail external availability need not block application liveness;
- readiness policy must follow current hardening conventions;
- health details protected by management-endpoint security.

## 26. Metrics and logging

### Safe metrics

- email send outcome;
- delivery type;
- bounded failure code;
- OAuth refresh outcome;
- retry count;
- transport latency histogram if current metrics support it.

Do not tag with:

- recipient;
- user/client ID;
- delivery ID;
- Gmail message ID;
- subject;
- token;
- branch ID;
- exception message.

### Logging

- no OAuth secrets/tokens;
- no Gmail raw payload;
- no email body;
- no attachment bytes/Base64;
- no invitation/recovery token;
- safe delivery ID and bounded failure code only where useful;
- expected provider rejection at appropriate log level;
- correlation IDs preserved;
- sanitized stack traces server-side only.

## 27. Package cleanup

Move provider-neutral components out of SMTP/Resend-specific packages.

Potential cleanup:

```text
notification.infrastructure.template
notification.infrastructure.gmail
notification.infrastructure.http
notification.configuration
```

Exact packages must follow repository conventions.

Do not perform gratuitous broad package rewrites. Move only classes whose current package falsely couples neutral functionality to a removed provider.

## 28. Removal scope

After Gmail replacement passes focused tests, remove:

- `SmtpEmailSender` and SMTP-specific adapters;
- SMTP-specific properties no longer used;
- Mailpit Docker Compose service;
- Mailpit health checks;
- Mailpit docs and UI references;
- SMTP test fixtures;
- `ResendEmailSender`;
- Resend properties;
- Resend health checks;
- Resend tests;
- `RESEND_*` documentation;
- provider-selection branches that only support removed transports;
- unused dependencies;
- obsolete feature flags.

Preserve:

- provider-neutral abstractions;
- domain delivery states;
- attempts and leases;
- templates/content;
- API;
- audit;
- metrics;
- idempotency;
- retry logic.

## 29. Docker Compose and local development

Mailpit must be removed from Compose after Gmail migration is complete.

Local development options:

### Safe default

```text
EMAIL_ENABLED=false
```

### Manual real Gmail testing

```text
EMAIL_ENABLED=true
GMAIL_LIVE_SEND_ENABLED=true
```

with secrets supplied through untracked environment configuration.

Standard local backend startup must not require Gmail credentials unless email is explicitly enabled.

Automated tests use the local HTTP stub and do not require Compose email services.

## 30. OpenAPI and API compatibility

Business delivery endpoints should remain stable where possible.

Update documentation to state:

- email is sent through Gmail API;
- `SENT` means accepted by Gmail for sending;
- failed deliveries may be retried according to current policy;
- recipient remains authoritative from client/staff data;
- normal endpoints do not accept arbitrary recipients;
- identity-email tokens are never exposed;
- provider details are not returned to ordinary users;
- no SMTP/Mailpit/Resend requirements remain.

Do not expose OAuth configuration or Gmail internals through public API schemas.

## 31. Persistence and Flyway decision

A migration is **not automatically required**.

Add a migration only if repository evidence proves a durable schema change is required, for example:

- safe provider message ID storage;
- uncertain-outcome state that cannot be represented by existing failure codes;
- provider field required for historical compatibility.

Prefer no migration when the existing delivery and attempt schema is sufficient.

If a migration is necessary:

- use the database migration safety workflow;
- verify next available version in Git history;
- preserve all existing SMTP/Resend delivery records;
- do not rewrite historical provider outcomes falsely;
- add PostgreSQL tests.

## 32. Events and audit

Existing email lifecycle events should remain provider-neutral.

Do not publish Gmail-specific public events unless an operational requirement proves necessary.

Audit records may indicate:

- delivery requested;
- delivery sent;
- delivery failed;
- retry requested;
- safe failure code.

Audit must not include:

- OAuth secrets/tokens;
- Gmail message raw payload;
- provider response body;
- tokenized invitation/recovery URL;
- email body;
- attachment bytes;
- full recipient where current privacy policy masks it.

Historical SMTP/Resend audit entries remain valid and are not rewritten.

## 33. Security requirements

1. OAuth secrets only from external secret configuration.
2. HTTPS required for production API/token endpoints.
3. Strict base-URL validation outside test profiles.
4. No secret in logs, errors, health, metrics, audit, OpenAPI, or repository.
5. Request bodies to OAuth/Gmail are never logged.
6. Refresh token is never persisted in business tables.
7. Access tokens remain memory-only and short-lived.
8. Recipient remains authoritative.
9. No generic relay endpoint.
10. Existing role, branch, and CSRF authorization remains unchanged.
11. Identity token links remain confidential.
12. Attachment integrity checked before sending.
13. Message size bounded.
14. HTTP response size bounded.
15. Errors sanitized.
16. Dependency versions reviewed.
17. Standard tests have no external network dependency.

## 34. Block dependency graph

```text
Block 1: Discovery, ADR, transport contract, and migration strategy
    -> Block 2: OAuth token client, typed configuration, and secret safety
        -> Block 3: Gmail MIME encoder and Gmail API sender adapter
            -> Block 4: Offline HTTP-stub, MIME, attachment, and error tests
                -> Block 5: Integrate all business email types and preserve delivery semantics
                    -> Block 6: Remove Mailpit, SMTP, Resend, and clean configuration/packages
                        -> Block 7: Optional live Gmail test, health, metrics, OpenAPI, and deployment guide
                            -> Block 8: End-to-end regression, security, architecture, and final gate
```

## 35. Progress model

- Block 1: 11%
- Block 2: 24%
- Block 3: 39%
- Block 4: 53%
- Block 5: 68%
- Block 6: 79%
- Block 7: 91%
- Block 8: 100%

Progress is earned only after block validation passes.

---

# Block 1: Discovery, ADR, transport contract, and migration strategy

- **Status:** `NOT_STARTED`
- **Target progress:** 11%

## Objective

Inventory the complete current email implementation, approve Gmail API as the only real transport, define OAuth/security/error/testing semantics, classify all retained/removed components, and decide whether a database migration is needed.

## Required discovery

Inspect:

- provider-neutral domain;
- SMTP/Mailpit implementation;
- Resend implementation;
- provider selection;
- composers/templates;
- all email types;
- delivery persistence/leases/retries;
- identity-token email confidentiality;
- audit/metrics/health;
- Compose/config/docs;
- current tests and dependencies;
- current migration history;
- module boundaries.

## ADR requirements

Create or update a focused ADR covering:

- Gmail API as sole real provider;
- dedicated Gmail account;
- HTTPS + OAuth 2.0;
- refresh-token secret strategy;
- access-token cache and concurrency;
- `PENDING/SENT/FAILED` meanings;
- uncertain outcome;
- provider message ID decision;
- MIME/attachment strategy;
- test strategy with local HTTP stub;
- optional live test;
- local email-disabled default;
- Mailpit/SMTP/Resend removal;
- health/metrics/logging;
- no-domain and $0 deployment assumptions;
- recruiter demo limitations;
- future migration to a transactional provider through `EmailSender` if the product becomes commercial.

## Authorized production scope

Only minimal provider-neutral or Gmail-facing contracts/configuration interfaces required to establish the design.

Do not implement OAuth HTTP calls or Gmail sending yet.

## Required tests

- provider selection policy;
- configuration contract bounds;
- delivery-state semantic contract;
- retry/uncertain outcome policy;
- no arbitrary-recipient policy;
- identity-token confidentiality contract;
- no public Gmail types in application contracts;
- no migration-required contract if ADR decides none;
- module/package boundaries.

## Explicit exclusions

- no OAuth implementation;
- no Gmail API call;
- no MIME implementation;
- no provider removal yet;
- no Compose changes;
- no migration unless Block 1 explicitly proves and authorizes one for a later block;
- no live email;
- no frontend.

## Validation

```powershell
.\gradlew compileJava compileTestJava
```

Run focused Block 1 tests, email domain/configuration regressions, and Spring Modulith verification if public contracts change.

## Completion criteria

- current implementation is fully classified;
- ADR resolves transport/security/testing decisions;
- migration decision is evidence-based;
- Gmail work remains within Block 1 boundaries;
- Codex reports and stops.

---

# Block 2: OAuth token client, typed configuration, and secret safety

- **Status:** `NOT_STARTED`
- **Depends on:** Block 1
- **Target progress:** 24%

## Objective

Implement validated Gmail/OAuth configuration and a concurrency-safe OAuth refresh-token client using only HTTPS and safe error translation.

## Authorized production scope

- `GmailApiProperties` or equivalent;
- `GoogleOAuthTokenClient`;
- access-token value object with expiration;
- concurrency-safe memory cache;
- OAuth error mapper;
- HTTP client configuration and timeouts;
- health/configuration readiness seam;
- test base-URL override.

## OAuth requirements

- form-encoded refresh request;
- client ID/secret/refresh token from secrets;
- strict timeout;
- bounded response body;
- JSON parsing allowlist;
- expiration safety margin;
- concurrent single-flight refresh;
- one cached token per application instance;
- forced refresh seam for Gmail `401` handling;
- secret redaction;
- no token persistence.

## Required tests

- configuration disabled/enabled;
- missing/invalid properties;
- HTTPS-required deployment URL;
- successful refresh;
- cache reuse;
- expiration refresh;
- forced refresh;
- concurrent single-flight;
- malformed/oversized response;
- `400`, `401`, `403`, `429`, timeout, and `5xx`;
- no secrets in exception, logs, `toString`, or metrics;
- no external network.

## Explicit exclusions

- no Gmail send adapter;
- no MIME;
- no provider removal;
- no live Gmail;
- no business service change.

## Validation

Compile, focused OAuth/configuration tests, HTTP-client security regression, secret scans, and architecture tests.

## Completion criteria

- OAuth tokens refresh securely;
- concurrency does not cause refresh storms;
- configuration fails safely;
- secrets remain external and redacted;
- Codex reports and stops.

---

# Block 3: Gmail MIME encoder and Gmail API sender adapter

- **Status:** `NOT_STARTED`
- **Depends on:** Blocks 1 and 2
- **Target progress:** 39%

## Objective

Implement valid MIME composition/encoding and the production Gmail API sender behind `EmailSender` without changing business delivery orchestration.

## MIME encoder requirements

- UTF-8 headers/body;
- CRLF compliance;
- plain-text + HTML alternatives;
- optional attachment;
- PDF and PNG;
- header-injection rejection;
- encoded subject/display name;
- safe filename;
- correct boundaries;
- correct transfer encoding;
- Base64 URL-safe raw message;
- bounded message size;
- no logging of generated MIME.

## Gmail sender requirements

1. Validate composed message.
2. Obtain OAuth access token.
3. Encode MIME.
4. Send HTTPS request.
5. Parse message ID.
6. Translate outcome to current `EmailSendResult`.
7. Force-refresh and retry once only for safe OAuth rejection.
8. Return uncertain outcome when acceptance cannot be determined.
9. Never internally retry general transport/provider failures without current workflow authorization.

## Required tests

- plain message;
- HTML/text multipart;
- receipt PDF;
- credential PNG;
- identity link email;
- UTF-8 accents;
- header injection;
- file names;
- exact attachment bytes after decode;
- URL-safe Base64;
- successful Gmail response;
- invalid response;
- safe auth-refresh retry;
- `400`, `403`, `429`, timeout, and `5xx` mapping;
- message too large;
- uncertain outcome;
- no request/body/secret logging;
- no Gmail/provider types outside infrastructure.

## Explicit exclusions

- no removal of old transports;
- no Compose/config cleanup;
- no live Gmail test;
- no API changes;
- no new business email type.

## Validation

Compile, focused MIME/Gmail adapter tests with HTTP stub, attachment integrity tests, privacy/log-capture tests, and architecture verification.

## Completion criteria

- Gmail adapter sends valid Gmail-compatible raw messages;
- all safe error mappings work;
- attachments remain exact;
- provider details remain isolated;
- Codex reports and stops.

---

# Block 4: Offline HTTP-stub, MIME, attachment, and error tests

- **Status:** `NOT_STARTED`
- **Depends on:** Blocks 2 and 3
- **Target progress:** 53%

## Objective

Build comprehensive deterministic test infrastructure around the production Gmail adapter and prove `clean check` requires no Internet, Gmail account, or secrets.

## Required test infrastructure

- local ephemeral HTTP stub server;
- separate OAuth and Gmail routes;
- request capture with test-only redaction;
- deterministic clocks;
- synthetic credentials;
- synthetic PDF/PNG fixtures;
- no committed real tokens/account data;
- bounded test payloads.

## Required scenarios

### OAuth

- all cases from Block 2;
- concurrent refresh;
- invalid grant;
- token expiry;
- malformed JSON;
- response size limit.

### Gmail

- all cases from Block 3;
- request headers;
- bearer token;
- exact endpoint and user path;
- response parsing;
- one auth retry only;
- no unsafe general retry;
- ambiguous timeout classification.

### MIME

- decode raw request;
- inspect MIME tree;
- validate text/HTML;
- validate receipt PDF;
- validate credential PNG;
- validate token link appears only where approved;
- confirm subject/from/to;
- confirm no internal storage path.

### Standard quality gate

Add a contract test proving standard tasks do not require:

```text
GMAIL_CLIENT_ID
GMAIL_CLIENT_SECRET
GMAIL_REFRESH_TOKEN
Internet access
```

## Explicit exclusions

- no real Gmail call;
- no old-provider removal;
- no business-service rewrite;
- no deployment.

## Validation

Run the full Gmail/OAuth adapter suite repeatedly to detect flakiness, plus compile and architecture tests.

## Completion criteria

- adapter tests are comprehensive and deterministic;
- no Internet or secrets are required;
- MIME and attachments are independently verified;
- Codex reports and stops.

---

# Block 5: Integrate all business email types and preserve delivery semantics

- **Status:** `COMPLETED`
- **Depends on:** Blocks 1 to 4
- **Target progress:** 68%

## Objective

Select Gmail as the active provider while preserving ADR-011's two distinct paths: durable transactional delivery for payment receipts and access credentials, and ephemeral identity-email contracts for staff invitations and account-security notices. Do not remove old transports yet.

## Integration requirements

- provider selection chooses Gmail when configured;
- standard test profile uses HTTP stub Gmail transport;
- email-disabled behavior remains intentional;
- existing service/domain API unchanged where possible;
- `EmailDeliveryType` remains restricted to `PAYMENT_RECEIPT` and `ACCESS_CREDENTIAL`;
- durable status/attempt/lease/idempotency/optimistic-lock/retry/audit/metrics behavior remains unchanged for those two types;
- canonical receipt PDF and access-credential PNG attachments are preserved byte-for-byte;
- Gmail message ID is persisted only in the existing bounded attempt field;
- provider failure codes are persisted safely, and ambiguous outcomes block blind retry according to ADR-011;
- identity messages use the same provider-neutral `EmailSender` and selected Gmail adapter, but never enter `email_deliveries` or `email_delivery_attempts`;
- identity message bodies and tokenized links are not persisted; invitation/recovery token rotation remains identity-owned, and an old token-bearing message is never generically retried;
- account activation and security notices are best-effort post-commit effects and transport failure does not roll back completed identity state;
- `gym.staff_account_activation_deliveries` remains bounded identity-domain operational metadata, not the generic email outbox, and contains no token or message content;
- retrying an activation notice does not rerun account provisioning or reuse an invitation token;
- identity messages remain attachment-free unless a separately approved requirement says otherwise.

## Required email-flow tests

### Financial and access

- payment receipt with canonical PDF;
- access credential with canonical PNG.

### Identity

- invitation;
- account activated;
- password recovery;
- password changed;
- suspended;
- reactivated;
- deactivated;
- role/scope changed;
- assignments changed.

For each durable flow verify:

- authoritative recipient;
- subject;
- text;
- HTML;
- canonical attachment behavior;
- idempotency;
- `PENDING -> SENT`;
- `PENDING -> FAILED`;
- retry behavior;
- audit/metrics;
- provider message ID and sanitized failure details;
- ambiguous result does not allow blind retry;
- source resource and attachment integrity remain unchanged on send failure.

For every ephemeral identity flow verify:

- authoritative staff recipient, subject, plain text, and HTML;
- invitation/recovery links appear only in the outgoing MIME body;
- no attachment and no generic durable delivery/attempt row;
- token, tokenized URL, provider response, or raw message does not leak into persistence, events, audit, metrics, logs, or safe errors;
- a resend composes and sends a newly rotated token rather than retrying an expired/revoked token-bearing message;
- transport failure is best-effort after identity state commits.

## Concurrency tests

- duplicate initial durable request;
- concurrent durable claims;
- concurrent durable retries;
- token refresh during concurrent deliveries;
- source unchanged on email failure;
- no duplicate local durable send attempt;
- invitation/recovery token rotation remains serialized by the identity domain.

## Explicit exclusions

- no removal of old providers until Gmail integration is proven;
- no live Gmail;
- no frontend;
- no new email workflow.

## Validation

Compile, all transactional-email tests through Gmail stub, identity provisioning regression, receipt/credential regression, audit/metrics regression, and Spring Modulith verification.

## Completion criteria

- both durable types work through Gmail adapter with durable lifecycle invariants unchanged;
- all ephemeral identity email types use Gmail through `EmailSender` without generic outbox rows or stored tokenized content;
- invitation/recovery token rotation and best-effort post-commit behavior remain correct;
- durable concurrency/idempotency remain correct;
- source aggregates remain isolated;
- SMTP, Mailpit, and Resend remain untouched until Block 6;
- Codex reports and stops.

---

# Block 6: Remove Mailpit, SMTP, Resend, and clean configuration/packages

- **Status:** `NOT_STARTED`
- **Depends on:** Blocks 1 to 5
- **Target progress:** 79%

## Objective

Remove provisional transports and obsolete infrastructure after Gmail parity is proven, while preserving provider-neutral composition and domain behavior.

## Required removal

- Mailpit Compose service;
- Mailpit ports/health/docs;
- SMTP adapter;
- SMTP properties and dependencies no longer used;
- SMTP tests and fixtures;
- Resend adapter;
- Resend properties and dependencies;
- Resend tests/health/docs;
- obsolete provider-selection branches;
- obsolete environment variables;
- misleading readiness checks;
- obsolete comments/TODOs.

## Package cleanup

- move template/composition classes out of provider-specific packages;
- update imports/tests narrowly;
- preserve public/application contracts;
- avoid broad unrelated package rewrites;
- ensure no `smtp`, `mailpit`, or `resend` runtime references remain except historical documentation explicitly explaining migration, if retained.

## Configuration cleanup

Final configuration should contain only:

- provider-neutral email domain options;
- Gmail/OAuth options;
- disabled-test/default behavior;
- optional live-test options.

Remove:

```text
SMTP_*
RESEND_*
MAILPIT_*
```

from active configuration and deployment templates.

## Required tests

- source scans for removed classes/config;
- Compose validation;
- profile binding;
- Gmail-only provider selection;
- no old dependency on classpath;
- all email-domain regressions;
- documentation link checks;
- no changes to persistence/history behavior.

## Explicit exclusions

- no live Gmail;
- no frontend;
- no provider fallback;
- no migration unless previously authorized.

## Validation

Compile, full email suite, Compose/configuration contracts, source scans, dependency review, and Spring Modulith verification.

## Completion criteria

- Gmail is the only real transport;
- Mailpit, SMTP, and Resend are removed cleanly;
- neutral templates/domain remain intact;
- no obsolete configuration remains;
- Codex reports and stops.

---

# Block 7: Optional live Gmail test, health, metrics, OpenAPI, and deployment guide

- **Status:** `NOT_STARTED`
- **Depends on:** Blocks 1 to 6
- **Target progress:** 91%

## Objective

Add opt-in real Gmail validation, finalize operational visibility, document OAuth setup and Render secrets, and align API/OpenAPI/frontend contracts.

## Optional live task

Provide a dedicated Gradle source set/task or equivalent repository-conventional mechanism:

```powershell
.\gradlew gmailIntegrationTest
```

Requirements:

- excluded from `check` and `clean check`;
- explicit enable flag;
- allowlisted recipient;
- bounded message count;
- synthetic text/HTML/PDF/PNG;
- API acceptance assertion;
- no inbox-delivery claim;
- secrets never printed;
- safe skip behavior.

## Health/readiness

- Gmail configuration readiness;
- email disabled/configured/invalid states;
- no external send from health probe;
- safe protected detail;
- OAuth/Gmail failures reflected through delivery metrics, not constant health traffic.

## Metrics

Verify bounded metrics for:

- OAuth refresh result;
- Gmail send result;
- delivery type;
- failure code;
- latency;
- retry.

No high-cardinality or sensitive tags.

## OpenAPI/API documentation

Update:

- `SENT` semantics;
- provider-neutral public API;
- retry/uncertain outcome;
- authoritative recipients;
- no arbitrary email relay;
- no body/token exposure;
- Gmail details only in operational/deployment docs, not ordinary business schemas.

## Deployment guide

Document exact deferred deployment preparation:

1. Create dedicated Gmail account.
2. Create Google Cloud project.
3. Enable Gmail API.
4. Configure OAuth consent for the dedicated account.
5. Create OAuth client.
6. Perform one-time authorization.
7. Obtain refresh token securely.
8. Configure Render secrets.
9. Set sender address/name.
10. Keep email disabled until smoke-test window.
11. Execute optional live test.
12. Enable application delivery.
13. Verify real receipt and credential emails.
14. Verify invitation and recovery links.
15. Review Gmail sent folder and Coach Gym delivery history.

Include revocation/rotation procedures for compromised refresh token/client secret.

## README/recruiter flow

Document a natural demonstration:

- authorized staff uses demo client or staff invitation flow;
- real email is sent through Gmail API;
- recruiter receives PDF/PNG or invitation/recovery message;
- application shows `SENT`;
- audit and attempt history are visible without secrets;
- Gmail acceptance is distinguished from guaranteed inbox delivery.

## Required tests

- live-task exclusion from normal check;
- configuration/health tests;
- metric tag contracts;
- OpenAPI contracts;
- documentation configuration-name checks;
- secrets absent from tracked files;
- optional live-test code uses no real default recipient.

## Explicit exclusions

- no mandatory real Gmail call;
- no actual deployment;
- no frontend;
- no domain purchase;
- no inbox polling.

## Validation

Compile, live-task configuration tests, health/metrics/OpenAPI suites, docs/source secret scans, and architecture verification.

## Completion criteria

- real Gmail can be tested safely on demand;
- normal quality gate remains offline;
- operations and deployment are documented;
- recruiter flow is clear and honest;
- Codex reports and stops.

---

# Block 8: End-to-end regression, security, architecture, and final gate

- **Status:** `NOT_STARTED`
- **Depends on:** Blocks 1 to 7
- **Target progress:** 100%

## Objective

Complete the branch with full Gmail-based delivery regression, security/privacy review, concurrency, configuration cleanup, documentation, architecture validation, and final quality gate.

## End-to-end stubbed scenarios

### Payment receipt

- request delivery;
- canonical PDF loaded;
- OAuth refresh;
- Gmail request accepted;
- delivery becomes `SENT`;
- attempt/audit/metrics correct;
- repeated request idempotent.

### Access credential

- canonical PNG loaded;
- exact attachment;
- no raw QR token in body/log/audit;
- delivery lifecycle correct.

### Staff invitation

- one-time activation link in email;
- token absent from delivery API/audit/log;
- resend rotates invitation token;
- old email link becomes invalid according to identity domain.

### Password recovery

- generic external request behavior;
- tokenized email;
- no enumeration;
- no token exposure;
- completion and security notification regression.

### Account lifecycle emails

- activation;
- password changed;
- suspended/reactivated/deactivated;
- role/scope changed;
- assignments changed.

## Failure scenarios

- OAuth invalid grant;
- OAuth timeout/rate limit/unavailable;
- Gmail invalid request;
- Gmail permission denied;
- Gmail rate limit;
- Gmail timeout;
- Gmail `5xx`;
- malformed response;
- message too large;
- source/attachment unavailable;
- ambiguous outcome;
- retry limit;
- stale version;
- source aggregates unchanged.

## Concurrency

- token-refresh single-flight;
- duplicate initial delivery;
- concurrent claims;
- concurrent retries;
- uncertain outcome not blindly retried;
- no duplicate local send ownership;
- no deadlock/partial attempt.

## Security review

- no Gmail/OAuth secret in Git;
- no token in logs/errors/health/metrics/audit/OpenAPI;
- no raw MIME/body/attachment logging;
- HTTPS validation;
- authoritative recipient;
- no generic relay;
- CSRF/role/branch authorization regression;
- identity-link confidentiality;
- request/response size limits;
- no external network in standard tests;
- no SMTP/Mailpit/Resend remnants.

## Persistence/history review

- existing historical SMTP/Resend delivery rows remain readable;
- no false rewriting of provider outcomes;
- current delivery/attempt constraints pass;
- no migration required unless explicitly implemented and tested;
- idempotency and lease invariants pass.

## Architecture review

- email domain provider-neutral;
- Gmail implementation confined to infrastructure;
- OAuth implementation confined to infrastructure/configuration;
- no provider types in application/public contracts;
- identity/payment/access modules depend only on public email commands/events;
- controllers remain thin;
- Spring Modulith passes.

## Repository hygiene

Reject:

- Gmail credentials/tokens;
- real email addresses in live-test defaults;
- raw MIME snapshots containing tokens;
- Mailpit service/config;
- SMTP/Resend runtime code;
- generated emails/attachments;
- `.env` files;
- temporary scripts;
- ZIP files;
- build output;
- BOM characters;
- HTML entities in source;
- debug logging;
- broad unrelated formatting.

## Required validation sequence

```powershell
.\gradlew compileJava compileTestJava
```

Run all Gmail/OAuth/MIME tests.

Run all transactional-email tests.

Run regressions for:

- staff invitations/provisioning/recovery;
- payment receipts;
- access credentials;
- storage;
- branches and authorization;
- audit/query/CSV;
- metrics/health;
- security/OpenAPI.

Run:

```powershell
.\gradlew test `
  --tests "*ApplicationModuleStructureTests.verifiesModuleBoundaries" `
  --rerun-tasks
```

Run source/configuration scans for removed providers and secrets.

Run:

```powershell
git diff --check
```

Finally:

```powershell
.\gradlew clean check
```

Do not run `gmailIntegrationTest` automatically. Report whether it was skipped or explicitly executed by authorized live-test configuration.

## Blueprint closure

Codex must report:

- all eight block statuses;
- final email architecture;
- OAuth/token guarantee;
- delivery-state semantics;
- uncertain-outcome behavior;
- removed Mailpit/SMTP/Resend components;
- all validated email types;
- standard offline-test guarantee;
- optional live-test status;
- exact blueprint sections implemented;
- required blueprint updates;
- next branch: `feature/multi-branch-reporting-audit`;
- deferred final hardening/deployment.

## Completion criteria

The branch reaches 100% only when:

- all eight blocks pass;
- Gmail API is the only real transport;
- Mailpit, SMTP, and Resend are removed;
- OAuth refresh is secure and concurrency-safe;
- Gmail MIME with text/HTML/PDF/PNG is valid;
- every business email type works through Gmail adapter;
- standard tests require no Internet or secrets;
- optional real Gmail test is safely isolated;
- delivery idempotency, leases, retries, and attempts remain correct;
- uncertain outcomes are handled honestly;
- identity tokens remain confidential;
- audit, logs, metrics, health, and OpenAPI protect secrets;
- historical deliveries remain readable;
- Spring Modulith passes;
- repository hygiene passes;
- `clean check` succeeds;
- final verdict is `PASS` or `PASS WITH NON-BLOCKING NOTES`;
- Codex stops.

---

# 36. Complete test matrix

## Configuration and OAuth

- disabled/enabled configuration;
- required secrets;
- HTTPS validation;
- token refresh/cache/expiry;
- concurrent refresh;
- forced refresh;
- OAuth errors/timeouts/rate limit;
- secret redaction.

## MIME and Gmail adapter

- text/plain;
- text/html;
- UTF-8;
- PDF;
- PNG;
- identity links;
- filenames/MIME;
- Base64 URL-safe;
- header injection;
- size limits;
- Gmail responses/errors;
- uncertain outcomes.

## Delivery domain

- `PENDING -> SENT`;
- `PENDING -> FAILED`;
- retry;
- retry limit;
- duplicate request;
- concurrent claims;
- optimistic locking;
- append-only attempts;
- source isolation.

## Business email catalog

- receipt;
- credential;
- invitation;
- activation;
- recovery;
- password changed;
- suspended/reactivated/deactivated;
- role/scope changed;
- assignments changed.

## Privacy/security

- authoritative recipient;
- no generic relay;
- no provider/token/body/attachment leakage;
- identity-token confidentiality;
- audit sanitizer;
- log capture;
- metric tag bounds;
- health privacy;
- CSRF/role/branch regression.

## Removal/configuration

- no Mailpit Compose service;
- no SMTP runtime classes/config;
- no Resend runtime classes/config;
- no obsolete environment variables;
- no removed dependencies;
- Gmail-only selection.

## Optional live testing

- excluded from normal check;
- explicit enablement;
- allowlisted recipient;
- synthetic data;
- bounded sends;
- API acceptance only;
- no secret output.

## Architecture/OpenAPI

- provider-neutral public contracts;
- Gmail/OAuth infrastructure isolation;
- no internal cross-module imports;
- thin controllers;
- Spring Modulith;
- stable business APIs;
- accurate `SENT` semantics.

# 37. Definition of Done

- [ ] Block 1 completed and validated
- [ ] Block 2 completed and validated
- [ ] Block 3 completed and validated
- [ ] Block 4 completed and validated
- [x] Block 5 completed and validated
- [ ] Block 6 completed and validated
- [ ] Block 7 completed and validated
- [ ] Block 8 completed and validated
- [ ] ADR completed
- [ ] Gmail API sole real transport
- [ ] OAuth 2.0 refresh-token flow
- [ ] Access-token cache and concurrent refresh
- [ ] HTTPS and typed configuration
- [ ] MIME text and HTML
- [ ] PDF and PNG attachments
- [ ] URL-safe Base64
- [ ] Safe Gmail/OAuth error mapping
- [ ] Honest `SENT` semantics
- [ ] Uncertain outcome policy
- [ ] Offline HTTP-stub tests
- [ ] No secrets required by `clean check`
- [ ] Optional live Gmail task
- [ ] Every approved email type validated
- [ ] Existing idempotency/leases/retries preserved
- [ ] Tokenized identity links confidential
- [ ] Mailpit removed
- [ ] SMTP removed
- [ ] Resend removed
- [ ] Compose/config/dependencies cleaned
- [ ] Health/metrics/logging safe
- [ ] OpenAPI aligned
- [ ] Deployment guide updated
- [ ] Historical deliveries preserved
- [ ] No real external network in standard tests
- [ ] Spring Modulith passes
- [ ] Flyway history valid
- [ ] Repository hygiene passes
- [ ] `clean check` passes
- [ ] Blueprint update requirements identified

# 38. Risks and mitigations

## Risk: refresh token compromise

**Mitigation:** dedicated Gmail account, Render secret storage, no persistence/logging, rotation and revocation runbook, minimum OAuth scope.

## Risk: OAuth refresh stampede

**Mitigation:** concurrency-safe single-flight cache and stress tests.

## Risk: Gmail accepted message but backend failed before finalization

**Mitigation:** explicit uncertain outcome, no blind retry, durable attempt, operator-visible warning.

## Risk: duplicate email from concurrent requests

**Mitigation:** existing PostgreSQL idempotency, claim leases, optimistic locking, and concurrency tests.

## Risk: malformed MIME breaks attachments

**Mitigation:** independent decode/parse tests and byte-for-byte attachment assertions.

## Risk: invitation/recovery token leaks in provider diagnostics

**Mitigation:** no raw request logging, sanitized provider errors, no body in audit/metrics/API.

## Risk: local development sends accidental real emails

**Mitigation:** email disabled by default, explicit live-send flag, allowlisted recipient for tests.

## Risk: recruiter uses the system as a relay

**Mitigation:** authoritative recipients, authenticated business actions, CSRF, rate limits, demo resources, no generic arbitrary-content endpoint.

## Risk: Gmail anti-abuse suspension

**Mitigation:** very low internal limits, dedicated account, no bulk/marketing mail, manual monitoring, bounded retries.

## Risk: tests become flaky through Internet dependency

**Mitigation:** local HTTP stub in standard tests; live Gmail isolated from quality gate.

## Risk: removing providers breaks neutral composer code

**Mitigation:** move only neutral classes, prove Gmail parity first, full email regression before deletion.

## Risk: historical delivery records reference removed provider

**Mitigation:** do not rewrite history; keep provider value readable when persisted; remove only runtime transport code.

# 39. Deferred work

The following belongs to later branches or future commercial evolution:

- multi-branch reporting and audit filters;
- final multi-branch hardening;
- frontend email UX;
- inbox-delivery confirmation;
- bounce/complaint webhooks;
- transactional email provider with verified custom domain;
- provider migration from Gmail to SES/Postmark/Resend/etc.;
- marketing email;
- bulk campaigns;
- inbound email;
- MFA;
- production deployment;
- custom domain purchase.

Expected next branch:

```text
feature/multi-branch-reporting-audit
```

# 40. Short prompts for Codex

## Start Block 1

```text
Read the active plan:
docs/branch-plans/gmail-api-email-delivery-plan.md

Implement only Block 1: Discovery, ADR, transport contract, and migration strategy.
Do not implement Blocks 2 through 8.
Follow AGENTS.md and the applicable skills, validate the authorized block, report evidence, and stop.
```

## Continue with a later block

```text
Review the current repository, approved ADR, and completed previous blocks.
Implement only Block <N>: <EXACT BLOCK NAME> from:
docs/branch-plans/gmail-api-email-delivery-plan.md

Do not implement later blocks. Validate, report, and stop.
```

## Repair without advancing

```text
The authorized scope remains Block <N>.
Diagnose and fix the failure below without starting the next block:

<PASTE FAILURE>

Rerun the smallest failing scope, then the block validation. Report evidence and stop.
```
