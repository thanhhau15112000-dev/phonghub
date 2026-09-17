# PhongHub implementor exchange log

Project: `G:\phonghub`
Branch at start: `staging`
Purpose: record every implementation prompt, its handle, the implementor response, and the verification state.

## Entry P1

- Handle: `P1-20260917-001`
- Kind: implementation prompt draft
- Destination: Antigravity implementor
- Sent: no
- Reason: the current Computer Use session does not expose the Antigravity native window (`apps: []`).

### Prompt

```text
You are the Implementor for the PhongHub repository. Work in G:\phonghub and use the current worktree as the source of truth.

Before editing:
1. Read context.md, README.md, docs/api-spec.yaml, the existing database migration, AGENTS.md, and the relevant local skills for hexagonal architecture, Spring Boot, security, TDD, and verification.
2. Record the starting HEAD and git status.
3. Preserve existing user changes, especially context.md. Do not reset, clean, or overwrite unrelated files.

Objective
Build and test the first runnable PhongHub MVP for property/room operations. This is a four-actor rental-management app: ADMIN, STAFF, TECHNICIAN, and TENANT. Implement the smallest complete vertical slice that proves the core domain and UI flows.

Architecture and boundaries
- Use Java 21 and Spring Boot.
- Use Ports and Adapters / Hexagonal Architecture: domain code must not depend on Spring, Supabase, JPA, or web classes; application use cases orchestrate inbound and outbound ports; adapters stay at the edges.
- Keep the actual Supabase dashboard, credentials, email provider, password reset, and production identity integration out of this task. Do not read, create, print, or commit secrets.
- Define an explicit identity/authentication port and a local deterministic adapter or demo profile so the core flows can be tested without a live Supabase account. Clearly mark the Supabase integration seam and any local-only behavior.
- Do not make the browser call Supabase business tables directly.

In-scope domain flows
1. Property management: ADMIN can create/list properties; STAFF and TECHNICIAN are limited to assigned properties; TENANT can see only their own active contract/room data.
2. Room lifecycle: AVAILABLE, RESERVED, OCCUPIED, MAINTENANCE. Enforce valid transitions and reject invalid transitions. Do not allow two active contracts for one room.
3. Contract lifecycle: create a contract for a permitted room, store one primary occupant and optional additional occupants, activate it so the room becomes OCCUPIED, and end it so the room becomes AVAILABLE unless a maintenance state is explicitly required. Enforce ownership/property scope and active-contract invariants.
4. Minimal maintenance flow so TECHNICIAN has a real path: ADMIN/STAFF creates a ticket for a permitted room, TECHNICIAN can accept/update it only within assigned property scope, and resolving the ticket allows the room to leave MAINTENANCE through an explicit valid transition. No SLA, parts, inventory, or scheduling module.
5. Role/property authorization must be represented in application use cases and covered by tests. Never rely only on a client-supplied role.

Explicitly out of scope
- Billing, meter readings, invoices, payments, deposits, refunds, settlement, checkout financial calculation.
- OAuth/social login, forgot-password email flow, real SMTP configuration, MFA setup, production Supabase dashboard changes.
- Notifications, chat, reporting, analytics, file uploads, inventory, and a full work-order system.

UI requirements
- Prefer a simple server-rendered Spring MVC/Thymeleaf UI if that keeps the project runnable with fewer moving parts.
- The UI should cover the main flows above, including useful empty, validation, authorization, and error states.
- Design direction: utilitarian B2B property-operations dashboard; simple editorial typography; maximum three color groups (paper/background, ink/text, one accent); flat borders; little or no shadow; no gradients; no excessive rounded corners; no decorative text, emoji, fake charts, or marketing sections.
- Keep labels short and make status/action hierarchy obvious. Maintain keyboard focus, readable contrast, and responsive layout.

Persistence and API
- Use the simplest maintainable local persistence that supports the core invariants and tests. If a database adapter is used, keep schema/migrations consistent with the domain and do not invent finance tables.
- Add thin controllers and request validation. Return consistent errors (Problem Details or an equally explicit project-wide format).
- Do not copy the draft api-spec.yaml blindly; context.md and the domain invariants take precedence. Note any API contract gap instead of hiding it.

Verification requirements
- Add focused unit tests for room transitions, contract uniqueness/activation/end, property scope, role permissions, and maintenance ticket transitions.
- Add controller/integration tests for the main happy paths plus invalid transition, duplicate active contract, unauthorized property access, empty state, and validation/error responses.
- Run the targeted tests, the complete test suite, and a real Maven compile/build. Maven may not be on PATH; use the available Maven installation or wrapper without installing unrelated dependencies.
- Review the final diff and git status. Do not commit, push, merge, deploy, or modify Supabase.

Handoff format
Finish with exactly one status:
IMPLEMENTATION_DONE_READY_FOR_VERIFICATION
or PARTIAL_BLOCKED
or HANDOFF_CONFLICT

Then report:
- implementor handle: `R1-20260917-001`
- starting HEAD and final git status
- files changed and what each does
- commands run and their results
- invariants and error paths covered
- known gaps or unverified runtime/Supabase boundaries
- any decision that requires the lead to choose

Return the handoff in your response; do not edit log1.md. The lead will record the prompt and response separately.
```

## Response R1

- Handle: `R1-20260917-001`
- Status: pending; no response received because the prompt has not been transmitted.

## Logging contract

For every later exchange, append a new entry without rewriting earlier entries:

1. Prompt handle, destination, sent time, and the exact prompt transmitted.
2. Response handle, received time, exact response or a faithful bounded excerpt, and the implementor status.
3. Independent reviewer status, commands/evidence, unresolved gaps, and the next prompt handle if another iteration is needed.

This log is an orchestration record, not proof that the implementation, build, Supabase integration, or runtime is complete. Those require independent verification.

## Timeline and result snapshot

All times below use `Asia/Saigon` (`UTC+07:00`).

### Snapshot S1

- Recorded at: `2026-09-17 14:07:07 +07:00` (`2026-09-17 07:07:07 UTC`)
- HEAD: `831cf9c7cb7bb917145dbccf318989ec201abadc`
- HEAD commit time: `2026-09-14 18:16:40 +07:00`
- HEAD subject: `feat(database): add base postgresql schema and openapi specification`
- Branch: `staging...origin/staging`
- Working tree result: `context.md` and `log1.md` are untracked; no other changes were reported by `git status --short --branch`.
- File check result: `pom.xml` absent; `src/main/java` absent; `context.md` present; `log1.md` present.
- Computer Use result: the current CUA surface exposed no native app (`apps: []`); only browser surfaces were available. Antigravity was not targetable.
- Transmission result: P1 was prepared but not sent to Antigravity.
- Implementor result: no response received; R1 remains `pending`.
- Code result: no implementor code has been added and no build/test has been run.
- Supabase result: no dashboard setting, credential, user, or data was changed in this exchange.

The exact timestamps of the earlier CUA probes were not returned by that tool, so this snapshot records their result at the first reliable timestamp available instead of inventing event times.

## Entry R1

- Handle: `R1-20260917-001`
- Received at: `2026-09-17 16:01:07 +07:00` (`2026-09-17 09:01:07 UTC`)
- Source: `G:\Codex\attachments\ad164a70-5652-4ddb-8bc3-0fc77f620731\pasted-text.txt`
- Prompt sent at: not provided by the external chat; not inferred.
- Reported status: `IMPLEMENTATION_DONE_READY_FOR_VERIFICATION`
- Reported starting/current HEAD: `831cf9c7cb7bb917145dbccf318989ec201abadc`
- Reported implementation: Maven/Spring Boot 4.1.0 scaffold; pure-Java domain; application ports/services; in-memory repositories and deterministic local identity; REST Problem Details; Thymeleaf UI; CSS; tests.
- Reported scope: room lifecycle, contract lifecycle, minimal maintenance, property-scope authorization for four roles, and a deferred Supabase production seam.
- Reported verification: `compile` PASS, `test-compile` PASS, `test` PASS with 45 tests and 0 failures/errors, `package` PASS.
- Reported Supabase boundary: no live Supabase integration; `SupabaseAuthenticationAdapter` is described as a future seam.
- Reported decision for later: connect Supabase JWT/JWKS verification and move persistence to Flyway/PostgreSQL in a later sprint.
- Independent result at receipt time: the repository contains untracked `pom.xml`, `mvnw.cmd`, `src/main/java/`, `src/main/resources/`, and `src/test/`, in addition to untracked `context.md` and `log1.md`; this confirms files exist but does not verify the handoff claims.
- Independent reviewer status: pending. No handoff claim is accepted as verified yet.

The full pasted handoff remains at the source path above. This entry records the material claims and the evidence boundary; subsequent reviewer findings must be appended, not retroactively rewritten.

## Reviewer R1-V1

- Reviewer handle: `REV-20260917-001`
- Review snapshot recorded at: `2026-09-17 16:07:07 +07:00` (`2026-09-17 09:07:07 UTC`)
- Verdict: `FAIL/PARTIAL`
- Source changes by reviewer: none. Only `log1.md` was updated for orchestration evidence.

### Independent verification

- `2026-09-17 16:01:54 +07:00`: `.\mvnw.cmd test` passed; 45 tests, 0 failures, 0 errors.
- `2026-09-17 16:02:58 +07:00`: local app started on port 8080.
- `2026-09-17 16:03` local smoke test: request without identity to `GET /api/properties` returned `200` and both seeded properties; `GET /dashboard` returned `200`.
- `2026-09-17 16:05` local smoke test: creating a maintenance ticket for seeded `RESERVED` room `P103` with `setRoomMaintenance=true` returned `409`, but the ticket remained in the in-memory property ticket list while the room remained `RESERVED`.
- `2026-09-17 16:06:18 +07:00` local smoke test: `PUT /api/rooms/ROOM_101/status` with `AVAILABLE` returned `200`; the room became `AVAILABLE` while `GET /api/contracts/CONTRACT_1` still returned `ACTIVE` for that room.
- `2026-09-17 16:06:23 +07:00`: local app stopped; port 8080 confirmed not listening afterward.
- `2026-09-17 16:06:57 +07:00`: `.\mvnw.cmd package` passed and created `target/phonghub-core-0.0.1-SNAPSHOT.jar`.
- Domain import check: no Spring/Jakarta/JPA/Web/Jackson/Supabase imports found under `com.phonghub.domain`.
- Count check: 67 main Java files and 7 test Java files present.
- UI smoke check: dashboard rendered in the local browser with flat borders, no visible gradient/heavy shadow, and the requested utilitarian layout. This does not validate all routes or responsive breakpoints.

### Findings

#### F1 — demo identity is fail-open outside an explicit demo profile

- Classification: `BUG`
- Evidence: `REPRODUCED LOCAL`
- Priority: `P1` if the app is reachable by any non-developer/staging/production user; lower only for an explicitly isolated local demo.
- Confidence: high. `PhongHubConfiguration.java:65-68` always wires `LocalDemoAuthenticationAdapter`; `LocalDemoAuthenticationAdapter.java:50-59` falls back to `DEMO_ADMIN`; `CurrentUserInterceptor.java:24-70` accepts `X-User-Id`/`asUser` and otherwise returns the fallback user. A request without identity returned `200` and admin-scoped data.
- Impact: an unauthenticated caller can read and, through the same application services, attempt admin operations. The Supabase JWT boundary is not active.
- Next check: gate the demo adapter behind an explicit local profile and fail closed without authentication; production must use verified Supabase JWT/user mapping. Add tests for no identity, forged identity headers, and non-demo profile startup.

#### F2 — room status API bypasses active-contract synchronization

- Classification: `BUG`
- Evidence: `REPRODUCED LOCAL`
- Priority: `P1` for the room/contract lifecycle release gate.
- Confidence: high. `RoomService.changeRoomStatus` authorizes the property and calls `room.transitionTo(targetStatus)` without checking an active contract. The local request changed seeded room `P101` to `AVAILABLE` while its seeded contract remained `ACTIVE`.
- Impact: the UI/API can advertise a vacant room while a tenant contract is active; later lifecycle operations observe contradictory state.
- Next check: define which manual transitions are allowed and enforce the active-contract invariant in the application service. Add a regression test for `ACTIVE contract + OCCUPIED room -> manual AVAILABLE`.

#### F3 — maintenance create has a partial-write failure path

- Classification: `BUG`
- Evidence: `REPRODUCED LOCAL`
- Priority: `P2`.
- Confidence: high. `MaintenanceService.java:73-78` saves the ticket before `room.putUnderMaintenance()`. For seeded `RESERVED` room `P103`, the request returned `409` but left the new `REPORTED` ticket stored.
- Impact: a failed command leaves a phantom unresolved ticket and can affect later maintenance-release rules.
- Next check: validate the room transition before saving, or make the operation transactional/rollback-safe; add a test asserting no ticket persists when the room transition is rejected.

#### F4 — `mvnw.cmd` is not a portable Maven wrapper

- Classification: `BUG`
- Evidence: `SOURCE ONLY`
- Priority: `P3`.
- Confidence: high. `mvnw.cmd:2` hardcodes `C:\Users\Admin\.m2\wrapper\dists\...`; `.mvn/wrapper` is absent. It works on this machine but will fail for another checkout/user.
- Next check: add a real Maven Wrapper or document/use a portable Maven command; do not commit a machine-specific absolute path.

### Residual and boundary

- The pure-domain import boundary and current test/build claims are verified locally.
- The Supabase production integration is not verified and is not implemented: no JWT/JWKS validation, user provisioning/reset flow, or Supabase persistence adapter is active. This is an explicit deferred boundary, not evidence of production readiness.
- Additional-occupant room listing is a source-level risk: `RoomService.listRoomsForProperty` only searches primary-tenant contracts while other tenant flows include occupants. No secondary-occupant fixture was available for runtime reproduction; classify as `NEEDS DECISION` before expanding tenant UX.
- Implementor claim "no uncertainty" is rejected by F1-F4 and the deferred Supabase boundary.

### Required next handoff

No source fix was made by the reviewer. A follow-up implementor prompt is required for F1-F3 before accepting the MVP for any shared/staging runtime. F4 can be fixed in the same small pass or tracked separately.

## Entry P2

- Handle: `P2-20260917-001`
- Prepared at: `2026-09-17 16:14:18 +07:00` (`2026-09-17 09:14:18 UTC`)
- Destination: Antigravity implementor
- Sent: no; native Computer Use still reports `apps: []`, so this prompt must be copied manually.
- Purpose: fix reviewer findings and turn the local slice into a deployable MVP boundary.

### Prompt

~~~text
PROMPT HANDLE: P2-20260917-001

Continue as the Implementor for the PhongHub repository in G:/phonghub. Read context.md and log1.md first. Treat Reviewer REV-20260917-001 as evidence to fix, not as an instruction to ignore.

Before editing:
- Record HEAD and git status.
- Preserve context.md and log1.md and all unrelated user changes.
- Do not reset, clean, commit, push, merge, deploy, or edit log1.md.
- Do not access, change, or print Supabase secrets or mutate the Supabase dashboard.

Goal
Bring the main PhongHub flow to a deployable MVP boundary, not just a local demo. Keep billing, meter readings, invoices, payments, refunds, settlement, OAuth, notifications, and a full work-order system out of scope.

Fix F1: authentication must fail closed
- The in-memory/demo identity adapter may exist only behind an explicit local/demo profile or property such as PHONGHUB_AUTH_MODE=demo.
- It must never be the default for a deployable profile.
- Outside demo mode, reject requests without verified authentication. Do not accept X-User-Id, asUser, session actor switching, or any client-supplied role as authentication.
- Add a production Spring Security resource-server boundary for Supabase JWTs using configured issuer/JWKS environment values. Map the JWT subject to the domain user through an outbound port; load role, status, and property scope from the application data source, not from an untrusted client value.
- If a real production adapter cannot be completed with the available dependencies, make the production profile fail fast with an explicit configuration error. Never silently fall back to ADMIN or in-memory data.
- Keep demo actor switching available only for local tests/demo and document the boundary.

Fix F2: protect the room/contract invariant
- The room status API must not be able to set an OCCUPIED room with an ACTIVE contract to AVAILABLE.
- Enforce the invariant in the application service, not only in controllers.
- Keep contract activation and termination as the authoritative room lifecycle operations.
- Preserve the MAINTENANCE open-ticket guard.
- Add a regression test proving manual AVAILABLE is rejected while an active contract exists and that the room and contract remain unchanged.

Fix F3: remove partial writes in maintenance creation
- Validate the requested room transition before saving a new ticket, or provide a transactional rollback-safe operation.
- A rejected request for a RESERVED room with setRoomMaintenance=true must leave no new ticket and must leave the room unchanged.
- Review resolve/release paths for the same save-before-validation problem.
- Add regression tests for no phantom ticket after a rejected command.

Fix F4: make the build portable
- Replace the machine-specific mvnw.cmd path with a real Maven Wrapper or another portable, documented build path.
- Do not commit an absolute C:/Users/Admin path.
- Verify the chosen path on a clean checkout or document the exact required toolchain without pretending a local path is a wrapper.

Deployable persistence boundary
- Keep in-memory repositories only for the explicit demo/test profile.
- Add the smallest production PostgreSQL adapter needed by the main flows: users/identity mapping, properties, staff-property assignments, rooms, tenants, contracts/occupants, and maintenance tickets.
- Use the existing ports; do not leak JDBC/JPA/Supabase classes into domain code.
- Add or correct Flyway migrations for the MVP tables and constraints. At minimum align the persisted role value with the domain ADMIN/STAFF/TECHNICIAN/TENANT and add a database-level unique active-contract-per-room constraint.
- Do not use or store application passwords in public.users; Supabase Auth owns credentials. Do not log tokens, passwords, service-role keys, or connection secrets.
- Do not enable DataSeeder in a production profile.
- Keep the existing finance tables out of runtime use if they are not needed for this slice; do not expand the domain into billing.

Deploy configuration
- Add a production profile/configuration with environment-variable placeholders for Supabase JWT issuer/JWKS and PostgreSQL connection settings.
- Missing production auth or database configuration must fail fast with a useful error.
- Add a minimal Dockerfile or equivalent reproducible packaging path and a documented health/readiness endpoint.
- Keep local demo startup documented separately from production startup.

Main-flow correctness
- Preserve property-scope authorization for ADMIN, STAFF, TECHNICIAN, and TENANT.
- Ensure an additional occupant can access their own active contract and room data consistently with the primary occupant, or document a concrete decision if the existing API intentionally differs.
- Keep RFC 7807 errors for validation, authorization, not-found, conflict, and malformed requests.

Verification
- Add focused regression tests for F1, F2, F3, additional-occupant access, and production/demo profile separation.
- Run the full test suite and a real Maven compile/package.
- If a live Supabase/Postgres test cannot run because credentials or external services are unavailable, report it as UNVERIFIED/BLOCKED; do not simulate it as production proof.
- Review the final diff and git status. Do not commit or push.

Handoff format
Finish with exactly one status:
IMPLEMENTATION_DONE_READY_FOR_VERIFICATION
or PARTIAL_BLOCKED
or HANDOFF_CONFLICT

Then report:
- implementor handle: R2-20260917-001
- starting and final HEAD/status
- files changed and purpose
- tests/build commands and results
- which production boundaries are implemented versus unverified
- remaining decisions or blockers
~~~

## Response R2

- Handle: `R2-20260917-001`
- Status: pending; P2 has not been transmitted because Antigravity is not exposed to Computer Use.

## Deployment addendum D1 — Cloudflare target

- Recorded at: `2026-09-17 16:16:23 +07:00` (`2026-09-17 09:16:23 UTC`)
- User target: Cloudflare Workers/Pages.
- Platform decision: use Cloudflare Workers + Containers for the current Spring Boot application. Do not configure it as Pages Functions.
- Evidence: official Cloudflare docs state that Pages Functions execute in the Workers runtime and support Workers-compatible JavaScript/Node APIs/Wasm; official Containers docs support existing applications in any runtime through a Docker image, a Worker container binding, and `wrangler deploy`. Containers are available on the Workers Paid plan.
- Constraint: if the user has only a Pages project and no Workers Containers entitlement, the current Spring Boot/Thymeleaf server cannot be deployed there unchanged. The alternatives are a separate backend origin or a frontend rewrite to a Workers-compatible stack.

### P2 Cloudflare amendment to send to the implementor

Add this to the P2 prompt:

~~~text
Cloudflare deployment target
- Target Cloudflare Workers + Containers, not Pages Functions. Keep the existing Spring Boot/Thymeleaf application as the containerized backend; do not rewrite the app into a fake Pages Function.
- Add a multi-stage Dockerfile for Java 21 Spring Boot. The image must run linux/amd64, listen on port 8080, use the production profile, and contain no secrets or demo seed data.
- Add the smallest Worker project needed to route requests to the container: package.json, wrangler.jsonc or wrangler.toml, and worker source using @cloudflare/containers. Follow the current Cloudflare Containers configuration: Container subclass, defaultPort 8080, container binding, Durable Object migration with new_sqlite_classes, and a fetch handler forwarding all application paths to the container.
- Add a health endpoint usable by Cloudflare/container checks. If using Spring Boot Actuator, include only the required dependency and expose health without exposing sensitive environment/configuration details.
- Add a deploy document with local validation and Workers Builds commands. Use npx wrangler deploy for the Worker/container path and document that Docker is required for a Dockerfile image build. Document npx wrangler containers list for post-deploy verification.
- Keep secrets out of git. Document the names and purpose of environment/secrets only: Supabase JWT issuer/JWKS, PostgreSQL connection, and any required application key. Do not add values. Use Cloudflare secret/config mechanisms at deployment time.
- Do not claim a Cloudflare deploy succeeded unless a real deploy and a request through the Worker URL were run. If account, plan, Docker, domain, or credentials are missing, report that boundary as UNVERIFIED/BLOCKED.
~~~

Cloudflare deploy remains unperformed; no Cloudflare dashboard state or credentials were changed by this task.

## Entry R2

- Handle: `R2-20260917-001`
- Received at: `2026-09-17 16:28:16 +07:00` (`2026-09-17 09:28:16 UTC`)
- Source: `G:\Codex\attachments\e57447fc-a667-4eb1-9979-fbae4a5eb419\pasted-text.txt`
- Prompt sent at: not provided by the external chat; not inferred.
- Reported status: `IMPLEMENTATION_DONE_READY_FOR_VERIFICATION`
- Reported scope: F1-F4 fixes, additional-occupant access, PostgreSQL adapters, Flyway V2, `!prod` in-memory vs `prod` PostgreSQL profiles, production validator, Dockerfile, `/health`, and 56 passing tests.
- Reported HEAD: unchanged at `831cf9c7cb7bb917145dbccf318989ec201abadc`; no commit/push.
- Independent workspace result at receipt: `README.md` modified; `.mvn/`, `Dockerfile`, Maven files, source, resources, migrations, and tests untracked; context.md/log1.md preserved.
- Independent reviewer status: pending. The handoff claims are not accepted as verified yet.
