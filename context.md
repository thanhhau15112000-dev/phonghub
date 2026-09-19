# PhongHub — Context Snapshot

> Snapshot date: 2026-09-17
> Repository: https://github.com/thanhhau15112000-dev/phonghub
> Local branch observed: staging
> Status: planning and architecture discovery; no backend implementation has started.

## 1. Purpose and evidence boundary

This file records the product decisions, repository facts, Supabase observations, and open decisions collected before backend implementation.

The attached api-spec.yaml is treated as a draft input, not as the final product contract. Current product decisions override conflicting parts of the draft. This file is context, not a replacement for a future approved API/domain specification.

Evidence labels used below:

- FACT: observed directly in the repository, Git state, Supabase dashboard, or cited provider documentation.
- DECISION: explicitly selected during product discussion.
- PROPOSAL: recommended direction that still needs confirmation if it changes behavior or scope.
- OPEN: unresolved decision or evidence gap.

No secret, API key, database password, service-role key, or user password belongs in this file.

## 2. Product goal

PhongHub is a rental-room/property management application connecting four actor groups:

- ADMIN: manages the rental properties, rooms, users, assignments, and operational data globally.
- STAFF: operates on properties assigned to that staff member.
- TECHNICIAN: handles technical/maintenance work for assigned rooms or tickets.
- TENANT: a renter linked to a contract and allowed to access only their own contract-related data.

The main business flow is the lifecycle of a room and its contract. The backend should be organized with Hexagonal Architecture (Ports and Adapters), keeping domain rules independent from Spring, PostgreSQL, Supabase SDKs, HTTP, and email providers.

## 3. MVP definition

MVP means the smallest coherent end-to-end product flow, not all 71 operations currently present in the draft API specification.

The currently agreed MVP boundary is:

1. Admin creates properties and rooms.
2. Admin provisions accounts for Staff, Technician, and Tenant.
3. Tenant signs in with a username and temporary password, then must change the password on first login.
4. Admin can manually reset passwords for Tenant, Staff, and Technician accounts.
5. Staff sees and manages only assigned properties.
6. Tenant is linked to a tenant profile and contract, with one primary occupant plus additional occupants.
7. The room lifecycle supports at least:

~~~text
AVAILABLE -> RESERVED -> OCCUPIED
                           |
                           v
                      MAINTENANCE
                           |
                           v
                       AVAILABLE
~~~

8. Contract and room changes enforce ownership, scope, and state invariants atomically.

Finance is explicitly deferred from the MVP: meter readings, invoices, payments, refunds, deposits as a payment gate, settlement, and checkout financial calculation are not part of the first vertical slice.

The following are also deferred unless later included explicitly: public self-signup, Google/OAuth login, self-service forgot-password, advanced reporting, and broad notification features.

### Open MVP decision

The product has a Technician actor, but it is not yet finally decided whether the MVP includes a minimal maintenance-ticket flow or only prepares the Technician role and room MAINTENANCE state. The recommended direction is a small ticket flow so all four actor groups have a real operational path:

~~~text
Admin/Staff creates ticket -> Technician accepts/updates -> ticket resolved -> room state can leave MAINTENANCE
~~~

Do not implement a full work-order/SLA/parts-management module without an explicit scope decision.

## 4. Authentication decision

### Selected direction

Use Supabase Auth Email/Password as the identity and session provider. Do not implement a second password hash, refresh-token store, or custom Spring JWT issuer.

The application does not use Google or other social login in the MVP.

The client talks only to the Spring API. Spring is the domain/API owner; Supabase is used behind adapters for Auth and PostgreSQL.

### Username login with hidden email identity

Supabase Email Auth authenticates with email/password, not username/password. The selected product behavior is therefore:

- Tenant-facing UI accepts username and password.
- public.users.username is unique and is not the Supabase identity itself.
- The backend resolves username to hidden auth_email and calls Supabase Auth server-side.
- The real email is used for identity and future recovery but is not returned to the tenant as the login identifier.
- The backend must return the same generic invalid-credential result for an unknown username and an invalid password; do not expose account existence through login errors.

The mapping belongs in an application port/adapter boundary, not in the domain model and not in frontend code.

### Account provisioning

- Account creation is admin-controlled; public self-signup should be disabled before production.
- Admin creates a Supabase Auth user with a real recovery email, a generated temporary password, and an auto-confirmed email when appropriate for the admin-provisioned flow.
- The domain profile stores must_change_password = true.
- The temporary password is shown to the admin only once, is never stored as plaintext, and is never written to logs.
- The first successful login is restricted to password change and logout until the password is changed.

### Manual password reset

Only ADMIN can manually reset passwords for TENANT, STAFF, and TECHNICIAN. Resetting another ADMIN is not part of the current policy.

Reset behavior:

1. Authorize the caller as an Admin.
2. Check the target account is within an allowed target role and is not the caller's prohibited target role.
3. Generate a temporary password on the server.
4. Call Supabase Admin updateUserById through a server-only adapter.
5. Set must_change_password = true and record an audit event.
6. Return the temporary password exactly once in the administrative response; never persist it or log it.
7. Invalidate or terminate old sessions where the provider behavior allows it. The backend must still enforce account status and must_change_password because an already-issued JWT can remain valid until its expiry.

Self-service forgot-password through email is deferred. Until that feature is explicitly added, Admin reset is the recovery path.

### Token and authorization boundary

- Client sends the Supabase access token to Spring with Authorization: Bearer.
- Spring Resource Server validates the token signature and standard claims (iss, sub, exp, and audience as configured) using the Supabase JWKS/public-key mechanism when available.
- sub identifies the Supabase Auth user UUID.
- public.users is the source of truth for domain role, account status, username, profile, and property scope.
- Domain authorization is checked in Spring/application use cases. A JWT role claim alone is not sufficient for property-scoped authorization.
- Custom Access Token Hooks and custom role claims are optional future optimizations, not the initial authorization source of truth.
- The Supabase service/secret key is server-only and must never reach the browser or mobile client.

## 5. Supabase project observations

Project inspected read-only:

- Project ref: ijtcqjsftodfiexvximm
- Project URL: https://ijtcqjsftodfiexvximm.supabase.co
- Region: ap-northeast-1 / Northeast Asia (Tokyo)
- Dashboard status: Healthy
- Dashboard showed no applied database migrations.
- Auth Users page showed no signed-up users. The page also displayed an estimated total counter; do not treat that estimate as confirmed user data without a second check.

### Auth settings observed

- Email provider: enabled.
- Phone and social providers: disabled.
- Public signup: currently enabled in the dashboard; this conflicts with the selected admin-provisioned product flow and must be disabled before production.
- Confirm email: enabled.
- Anonymous sign-in and manual identity linking: disabled.
- Access-token expiry: 3600 seconds.
- Compromised refresh-token detection: enabled.
- Refresh-token reuse interval: 10 seconds.
- Single-session, time-boxed-session, and inactivity-timeout controls were unavailable/disabled on the current plan.
- Site URL: http://localhost:3000.
- Redirect URL list: empty.
- Custom SMTP: disabled; default templates are active.
- Auth Hooks: none configured.
- CAPTCHA protection: disabled.
- TOTP MFA: enabled at the project setting level; no user enrollment was observed.

### Email delivery

The default Supabase email service is for development/testing and is not the production delivery plan for tenant recovery/invites. The project currently has no custom SMTP configuration.

Supabase accepts standard SMTP, so the backend should not depend on a Resend/Brevo SDK. Keep email provider configuration at the infrastructure boundary. Candidate free tiers observed during research, subject to re-verification before deployment:

- Resend: 3,000 emails/month and 100/day.
- Brevo: 300 emails/day.
- SMTP2GO: 1,000 emails/month and 200/day.
- Mailtrap Email API/SMTP: 4,000 emails/month and 150/day; Mailtrap Sandbox is suitable for development tests.

The initial MVP does not require email-based password recovery, so SMTP is not a blocker for the first auth vertical slice. Configure custom SMTP before enabling email reset/invite flows for real tenants.

Official references:

- https://supabase.com/docs/guides/auth/auth-smtp
- https://supabase.com/docs/guides/auth/passwords
- https://supabase.com/docs/guides/auth/jwts
- https://supabase.com/docs/guides/auth/sessions
- https://supabase.com/docs/reference/javascript/auth-admin-createuser
- https://supabase.com/docs/reference/javascript/auth-admin-updateuserbyid
- https://supabase.com/docs/guides/auth/auth-hooks

## 6. Repository state

The repository currently contains a design/schema starting point rather than a runnable Spring backend:

~~~text
.env.example
README.md
docs/api-spec.yaml
src/main/resources/db/migration/V1__init_base_schema.sql
.agents/skills/*
~~~

Observed gaps:

- No pom.xml or build.gradle was present.
- No src/main/java application was present.
- No automated test suite was present.
- No verified local runtime or build exists yet.
- .env.example already references the Supabase project, region, URL, publishable/anon key placeholder, service-role key placeholder, and JDBC connection placeholder. Actual secrets must remain outside Git.

## 7. Draft API/schema findings

docs/api-spec.yaml and the supplied Downloads copy were semantically identical after normalizing line endings. The draft parses as YAML and has 56 paths and 71 operations, but it is not implementation-ready as the product contract.

Important conflicts and gaps:

- Operations have no operationId, making use-case/API traceability harder.
- The API role enum contains TENANT, STAFF, and LANDLORD_ADMIN, but not TECHNICIAN; the product role vocabulary should be aligned to ADMIN, STAFF, TECHNICIAN, and TENANT.
- The draft auth API expects email/password and owns access/refresh-token behavior; this conflicts with the selected username alias plus Supabase Auth flow.
- The draft has no complete per-operation role/property permission matrix.
- PATCH endpoints reuse create-input schemas with required fields, so partial updates are not well-defined.
- Payment allocation, overpayment, checkout settlement, and financial state requirements are not aligned with the SQL schema and are explicitly deferred from MVP.
- The SQL users table currently contains password_hash; this duplicates Supabase credential storage and should be removed or retired before implementing Supabase Auth.
- The current SQL role check uses LANDLORD_ADMIN; it must be aligned with the selected ADMIN vocabulary.
- users.id is currently generated independently; the target design should align the domain user identity with auth.users.id or introduce an explicit, consistently enforced auth-user mapping.
- Existing staff/property assignments are a starting point for property scope but do not yet define the complete Technician assignment model.

The draft file must not be copied into controllers directly. First approve domain invariants and the auth/data contract, then derive OpenAPI from those decisions.

## 8. Hexagonal architecture direction

Recommended Java package direction:

~~~text
src/main/java/.../
  domain/
    user/
    property/
    room/
    contract/
    maintenance/
  application/
    port/in/
    port/out/
    usecase/
  adapter/in/web/
  adapter/out/supabase/
  adapter/out/postgres/
  adapter/out/audit/
  config/
~~~

Boundary rules:

- Domain entities and policies import no Spring, JPA, JDBC, Supabase, HTTP, or email classes.
- Inbound REST adapters translate HTTP requests into application use-case inputs.
- Application use cases orchestrate workflows through inbound/outbound ports.
- Outbound ports describe capabilities, not technologies: IdentityProviderPort, AuthTokenVerifierPort, UserRepositoryPort, PropertyAccessPort, RoomRepositoryPort, ContractRepositoryPort, AuditPort, and a clock/ID abstraction where needed.
- Supabase Auth Admin calls and JWT verification live in outbound adapters.
- PostgreSQL/Flyway repositories live in outbound adapters.
- Spring configuration is the composition root that wires concrete adapters to ports.
- No controller should call a Supabase client or repository directly.

## 9. Core invariants to preserve

### Identity and access

- One domain user maps to one Supabase Auth identity.
- Username is unique, case-normalized, and never used to expose the hidden email.
- password_hash is not stored in the public domain schema.
- Account status (ACTIVE, INACTIVE, SUSPENDED) is checked by the backend.
- must_change_password blocks business operations until the temporary password is replaced.
- Admin-only provisioning and reset cannot be bypassed by direct client calls.
- Every property-scoped query and command applies the caller's property scope.
- Tenant access is restricted to its own tenant/occupant/contract data.

### Room and contract

- A room cannot have two active contracts at the same time.
- A room cannot be marked OCCUPIED without a valid active contract/occupancy relation.
- A contract cannot attach to a room outside the caller's property scope.
- Primary occupant and additional occupants must be represented consistently and without duplicate active occupancy.
- Room transitions are explicit and reject invalid transitions instead of silently overwriting state.
- Concurrent contract creation/room assignment must be protected by database constraints and/or a transaction with a conflict response.
- Maintenance state must not be cleared by an unrelated room update.

### Deferred finance boundary

No room/contract MVP transition may depend on invoice, payment, meter, deposit settlement, refund, or overpayment logic.

## 10. Candidate application interfaces

These are planning interfaces, not yet approved OpenAPI schemas:

~~~text
POST /api/auth/login
  input: username, password
  output: Supabase access token/session data plus domain user summary

POST /api/auth/refresh
  input: refresh token
  output: refreshed Supabase session data

POST /api/auth/logout
  authenticated request

POST /api/auth/change-password
  authenticated request: new password

POST /api/admin/users
  input: username, hidden recovery email, profile, role
  output: created user summary and one-time temporary password

POST /api/admin/users/{userId}/password-reset
  output: one-time temporary password and reset metadata
~~~

The final API contract must define error codes, idempotency, rate limits, audit behavior, and whether access/refresh tokens are returned as JSON or managed through secure cookies after the client platform is confirmed.

## 11. Recommended implementation order

1. Confirm the remaining MVP decision for Technician maintenance tickets.
2. Bootstrap the Spring Boot project, build, configuration, error model, validation, and security test baseline.
3. Create the auth/domain migration: remove duplicate credential storage, add username and must_change_password, align role values, and align domain user IDs with Supabase Auth identities.
4. Implement the Supabase Auth adapter and Spring JWT resource-server validation without exposing the service key.
5. Implement the auth vertical slice: admin provisioning, username login, refresh/logout, forced password change, admin reset, and audit.
6. Implement property/room lifecycle and property-scoped authorization.
7. Implement tenant profile, primary/additional occupants, and contract lifecycle.
8. Add the minimal Technician maintenance flow only if confirmed in MVP scope.
9. Derive and replace the draft OpenAPI contract from the implemented domain/use-case boundaries.
10. Add unit, adapter integration, security, concurrency, and end-to-end tests for the invariants above.

## 12. Evidence gaps before implementation

- The current repository has no runnable Spring application, so no build or runtime behavior has been verified.
- The Supabase database has no dashboard-applied migrations at the time of inspection; the exact deployment/migration baseline must be confirmed before applying Flyway.
- The exact JWT signing-key algorithm/JWKS availability for this project was not confirmed from the dashboard; verify it during security adapter implementation.
- No custom SMTP, production Site URL, or Redirect URL is configured.
- No Auth Hook or custom role-claim strategy is configured.
- The final client platform and token-storage model are not yet confirmed.
- The Technician maintenance-ticket boundary remains open.

## 13. Working rule

Use this file as a context snapshot for future implementation/review work. Update it when a product decision changes. Do not treat unresolved items or proposals as implemented behavior, and do not treat the old API spec as authority over the decisions recorded here.
