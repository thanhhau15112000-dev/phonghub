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

## Entry REV-20260917-004

- Handle: `REV-20260917-004`
- Reviewed at: `2026-09-17 19:09:11 +07:00`
- Request: Explain the current Hexagonal Architecture deviations and the target organization under the mandatory Spring + Hexagon rule.
- Result: The core domain/application dependency scan is clean, but adapter direction and capability boundaries are not strict.

### Current topology

`domain -> application ports/services -> adapter in/out -> config` exists conceptually. The concrete violations are:

1. Inbound web classes import the concrete outbound `LocalDemoAuthenticationAdapter` (`CurrentUserInterceptor`, `GlobalUiAdvice`, `UserSwitchUiController`).
2. The in-memory persistence adapter `DataSeeder` imports the identity adapter for demo IDs.
3. JWT/Spring Security classes are placed under `adapter.out.identity` even though a bearer token enters through the inbound HTTP boundary.
4. `CurrentUserPort` mixes read identity with demo/test mutation (`setCurrentUser`, `clear`), forcing the production adapter to throw `UnsupportedOperationException`.
5. `AuthService` owns `SecureRandom` and `Instant.now()` directly instead of receiving credential/time capabilities through ports.
6. Supabase Auth uses JDK `HttpClient` and a manually-created ObjectMapper instead of Spring-managed HTTP infrastructure.
7. Transaction ownership is split between use-case decorators and a repository `@Transactional`; the in-memory transaction manager is no-op and tests only verify rollback invocation.
8. `AuthApiController` exposes the domain `User` directly instead of mapping domain output to an inbound API response DTO.

### Target topology

`HTTP/JWT -> adapter.in.web/security -> application.port.in -> application.service -> application.port.out -> adapter.out.persistence/supabase`

`config` is the only place that knows concrete adapters and wires Spring beans. The core remains framework-free; this is required for Hexagon and does not conflict with using Spring everywhere at the runtime boundary.

## Entry REV-20260917-005

- Handle: `REV-20260917-005`
- Reviewed at: `2026-09-17 19:11:04 +07:00`
- Request: Check whether prompt `P5-20260917-001` fully fixes the Hexagonal Architecture deviations described in `REV-20260917-004`.
- Result: `PARTIAL`. P5 covers the critical auth, JWT, transaction, Spring HTTP, demo-adapter coupling, and cross-system consistency issues, but it does not state two topology constraints explicitly enough.

### Required addendum

1. Move inbound JWT/Spring Security bridge classes from `adapter.out.identity` to `adapter.in.security`; keep only Supabase Auth HTTP calls under `adapter.out.supabase.auth`.
2. Do not expose domain entities from inbound controllers. Add API response DTOs and explicit mappings, starting with `AuthApiController` returning `User` directly.
3. Keep `config` as the only concrete-adapter wiring point and remove/deprecate unused duplicate `SupabaseAuthenticationAdapter` after reference verification.

## Git state change observed after review start

- Observed at: `2026-09-17 16:34:51 +07:00` (`2026-09-17 09:34:51 UTC`) via `git reflog`.
- The workspace moved from the earlier `feat/content` worktree state to commit `8c2c859948f2918f0a55d4baead29b800ba942f1` with message `chore: publish project content`; branch is now `feat/content`, tracking `origin/feat/content` at the same commit.
- This commit includes deletion of `.gitignore` and generated `target/` files, in addition to the implementation/content files. `.env` remains untracked. No commit/push command was issued by this reviewer; this is recorded as an observed external/workspace state change.
- The R2 handoff statement “no commit/push” is therefore stale against the current Git state. Do not reset, rewrite, or delete this commit in the reviewer turn; resolve the release hygiene issue in the next Implementor pass with explicit user-controlled Git steps.

### Current snapshot

- Captured at: `2026-09-17 16:37:48 +07:00` (`2026-09-17 09:37:48 UTC`).
- Branch/HEAD: `feat/content` / `8c2c859948f2918f0a55d4baead29b800ba942f1`.
- Working tree: `M log1.md`, `?? .env`; `origin/feat/content` points to the same HEAD.

## Entry P4

- Handle: `P4-20260917-001`
- Prepared at: `2026-09-17 16:43:36 +07:00` (`2026-09-17 09:43:36 UTC`)
- Destination: Antigravity implementor
- Starting branch/HEAD: `feat/content` / `8c2c859948f2918f0a55d4baead29b800ba942f1`
- Sent: no; native Computer Use still reports `apps: []`, so m must copy this prompt manually.
- Purpose: continue development and raise the MVP to a safe, deployable main-flow boundary using the independent findings in `REV-20260917-002`.

### Prompt

~~~text
PROMPT HANDLE: P4-20260917-001

Continue implementing PhongHub in G:/phonghub as the Implementor. Read context.md and log1.md first. Start from the actual current branch/HEAD/status; do not assume the handoff is current. The current known baseline is branch feat/content at 8c2c859, but verify it yourself.

Before editing:
- Preserve context.md, log1.md, .env, and unrelated user changes.
- Do not reset, clean, revert, commit, push, merge, or deploy.
- Never print or log Supabase keys, service/admin keys, database passwords, JWTs, temporary passwords, or .env values.
- Do not edit log1.md; the coordinator records the prompt and result.
- Keep the domain/application layers free of Spring, JDBC, Supabase, HTTP, and provider-specific classes.

Objective
Continue the MVP toward a safe main-flow release for the four actors ADMIN, STAFF, TECHNICIAN, and TENANT. The core flow is property -> room -> contract/occupants -> room lifecycle -> maintenance. Keep billing, meter readings, invoices, payments, refunds, settlement, OAuth, public signup, forgot-password, notifications, and broad work-order features out of scope.

Priority 1 — production authentication must be real or fail closed
- Keep LocalDemoAuthenticationAdapter and X-User-Id/asUser/session actor switching only in an explicit local/demo profile.
- In prod, wire a real Spring Security resource-server boundary for Supabase JWTs using configured issuer/JWKS and standard claims. Add only the required dependencies/configuration.
- Resolve the verified JWT subject to the domain user/profile in PostgreSQL. Load role, status, must_change_password, and property scope from server data; never trust client role claims or headers.
- Reject requests with no token, malformed token, invalid signature/issuer/audience, missing domain user, inactive/suspended user, or blocked must_change_password state as appropriate.
- Do not use DEMO_ADMIN as a production fallback. The production profile must never use in-memory repositories or DataSeeder.
- Add tests for the negative and successful paths. If a real Supabase adapter cannot be completed with available project dependencies, make prod fail closed with a clear configuration error and report PARTIAL_BLOCKED instead of claiming completion.

Priority 2 — implement the agreed auth MVP
- Implement the smallest backend auth boundary from context.md: username -> hidden Supabase auth email login, session/refresh/logout as needed by the chosen client, and first-login forced password change.
- Implement ADMIN-only manual password reset for TENANT/STAFF/TECHNICIAN through a server-only Supabase Admin adapter. Generate the temporary password server-side, set must_change_password=true, return it once only to the authorized ADMIN, never store/log it, and add an audit boundary if supported by the current schema.
- Return generic invalid-credential responses and RFC 7807 errors. Do not expose account existence.
- Align public.users.id with Supabase auth.users.id or add an explicit enforced mapping. Update migrations and repositories consistently. Do not reintroduce password_hash.

Priority 3 — make lifecycle persistence atomic
- Add transaction boundaries for maintenance create/resolve, contract activate/terminate, and contract plus occupants synchronization.
- A second-write failure must roll back the first write. Preserve validation-before-save for RESERVED-room maintenance and the active-contract/room-status invariant.
- Add fault-injection tests for room/ticket, room/contract, and contract/occupant failure paths.
- Verify PostgreSQL SQL against V1/V2 schema, including role values, tenant mapping, room/property scope, maintenance joins, and the active-contract partial unique index.

Priority 4 — finish the usable MVP UI without design bloat
- Complete the main UI states for property, room, contract, occupant, and maintenance flows: empty state, validation error, unauthorized/forbidden, not-found, conflict, loading/redirect, and success feedback.
- Keep the existing taste direction: simple flat surfaces, maximum three broad colors, minimal text, strong hierarchy, limited corner rounding, no unnecessary gradients or decorative cards. Do not add unrelated pages.
- Ensure tenant/occupant views show only their own contract/room data and staff/technician views respect property assignment.

Priority 5 — prepare the requested Cloudflare deployment path
- Use Cloudflare Workers + Containers for the Java 21 Spring Boot/Thymeleaf server; do not pretend it is a Pages Function.
- Add the smallest wrapper: package.json, wrangler.jsonc or wrangler.toml, Worker source using @cloudflare/containers, container binding, defaultPort 8080, Durable Object migration/new_sqlite_classes configuration, and fetch forwarding for all application paths.
- Keep the Docker image non-root, linux/amd64-compatible, port 8080, production-profile oriented, and free of secrets/demo seed data.
- Add .dockerignore and restore the repository .gitignore. Never include .env, target/, caches, or secrets in Git or Docker context.
- Document only secret/config names and local validation commands. Do not claim Cloudflare deployment or URL health until a real account deploy and request are available; otherwise report UNVERIFIED/BLOCKED.

Verification and handoff
- Run focused tests, the full test suite, and a real clean Maven package.
- Run production-profile tests and any available PostgreSQL/Flyway integration test without exposing secrets. If external services/credentials are unavailable, state the exact boundary.
- Check git diff/status and report the actual final HEAD. Do not commit or push.
- Finish with exactly one status: IMPLEMENTATION_DONE_READY_FOR_VERIFICATION, PARTIAL_BLOCKED, or HANDOFF_CONFLICT.
- Report changed files, behavior completed, tests/build results, production auth evidence, persistence evidence, UI evidence, Cloudflare evidence, and remaining blockers.
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

## Reviewer R2-V1

- Handle: `REV-20260917-002`
- Reviewed at: `2026-09-17 16:34:54 +07:00` (`2026-09-17 09:34:54 UTC`)
- Reviewer mode: independent, read-only for source/config; only this orchestration log was appended.
- Target: current workspace on branch `feat/content`, HEAD `831cf9c7cb7bb917145dbccf318989ec201abadc`.
- Verdict: `FAIL` — not ready for a shared/staging/production deployment.

### Independent verification

- `& .\\mvnw.cmd test` completed at approximately `2026-09-17 16:28:43 +07:00`: `56` tests, `0` failures, `0` errors, `BUILD SUCCESS`.
- `& .\\mvnw.cmd clean package` completed at approximately `2026-09-17 16:31:46 +07:00`: compile/package succeeded and the same `56` tests passed.
- Production profile smoke start at `2026-09-17 16:33:15 +07:00` exited with code `1` because `SPRING_DATASOURCE_URL` was unresolved. This confirms the profile does not start without configuration, but the observed error came from the datasource placeholder before the custom validator produced its intended message.
- Default local JAR started on port `18081` at `2026-09-17 16:34:24 +07:00`. `GET /api/properties` without any identity header returned HTTP `200` and two seeded properties. The process was stopped at `2026-09-17 16:34:41 +07:00`.
- `docker version` succeeded (`29.6.2`). No Docker build was run because `.gitignore` is deleted in the worktree and there is no `.dockerignore`; a build context could include the local `.env` and generated `target/` artifacts.
- Worker scan: `NO_WORKERS_FILES` for `wrangler`, `package.json`, and Worker source. README documents Docker only.

### Verified as fixed or locally covered

- Previous room invariant F2 is implemented in `RoomService.changeRoomStatus`: an OCCUPIED room with an ACTIVE contract cannot be manually changed to AVAILABLE. `RoomContractInvariantTest` covers unchanged room and contract state.
- Previous rejected-maintenance F3 path is covered: the room transition is validated before the new ticket is saved for a RESERVED room. `MaintenanceNoPartialWriteTest` passed.
- Maven wrapper is no longer the previously observed hardcoded `C:\\Users\\Admin` path; the wrapper properties are present and `mvnw.cmd` ran successfully on this machine.
- Additional occupant unit/integration coverage is included and passed locally.

### Findings

#### F1 — production still uses local demo identity and fails open as ADMIN

- Classification: `BUG`
- Evidence: `REPRODUCED LOCAL` plus `SOURCE ONLY` for production wiring.
- Priority: `P1` security/release gate.
- Confidence: high.
- Evidence: `PhongHubConfiguration` always exposes `LocalDemoAuthenticationAdapter` as the `CurrentUserPort`; `WebMvcConfig` injects that concrete adapter; `CurrentUserInterceptor` ignores headers only when demo is disabled and then uses the adapter fallback; `LocalDemoAuthenticationAdapter` defaults that fallback to `DEMO_ADMIN`. `SupabaseAuthenticationAdapter.getCurrentUser()` still throws `UnsupportedOperationException` and is not wired. A local request without identity returned `200` with admin-scoped property data.
- Impact: a production request without a verified Supabase identity is treated as the demo admin. Disabling `X-User-Id`/`asUser` prevents actor switching but does not establish authentication or fail closed.
- Next check: add a real production authentication boundary (JWT/resource server or an explicitly complete server-side Supabase adapter), map verified `sub` to the domain user, reject missing/invalid identity, and keep the demo adapter behind a local-only profile. Add a no-identity production test.

#### F2 — required Supabase auth vertical and manual admin password reset are absent

- Classification: `BUG`
- Evidence: `SOURCE ONLY`
- Priority: `P1` for the agreed MVP/auth release gate.
- Confidence: high.
- Evidence: repository search finds no Spring Security/resource-server dependency, no bearer/JWT filter, no login/change-password/reset controller or service, and no Supabase Admin adapter. The only Supabase class is an explicit seam/stub. V2 removes `password_hash` and adds `username`/`must_change_password`, but no code provisions Supabase users, resolves username to hidden auth email, enforces first-login password change, or calls the server-only admin reset operation.
- Impact: users cannot authenticate through the production app and the agreed ADMIN manual reset flow cannot run. The profile can compile but cannot provide the required identity lifecycle.
- Next check: implement the smallest auth vertical from `context.md`; keep service/admin keys server-side, return a temporary password only once to an authorized ADMIN, never persist or log it, and add authorization/negative-path tests.

#### F3 — multi-write production operations are not transaction-safe

- Classification: `BUG`
- Evidence: `SOURCE ONLY`; no live PostgreSQL failure injection was available.
- Priority: `P2` data consistency/release risk.
- Confidence: high.
- Evidence: `MaintenanceService.createTicket` saves the room before saving the ticket; `resolveTicket` saves the ticket before releasing/saving the room. `ContractService` saves room and contract separately, while `PostgresContractRepository.save` writes the contract, deletes occupants, and inserts occupants in separate JDBC calls. No `@Transactional` boundary or equivalent rollback contract is present.
- Impact: a database/network/constraint failure after the first write can leave a room, contract, or occupant set inconsistent even though the command failed.
- Next check: put each multi-aggregate persistence operation behind one transaction boundary or an explicit transactional port, then add fault-injection tests for the second-write failure. Keep the already-fixed validation-before-save path.

#### F4 — requested Cloudflare deployment path is not configured

- Classification: `BUG`
- Evidence: `SOURCE ONLY`
- Priority: `P1` for the stated deployment target.
- Confidence: high.
- Evidence: the workspace has a Java Dockerfile but no `package.json`, `wrangler.jsonc`/`wrangler.toml`, Worker source, container binding, or Durable Object migration. No Cloudflare deploy or request through a Worker URL was performed.
- Impact: the current repository cannot be deployed through the requested Cloudflare Worker/Pages path from the checked-in artifacts. Pages Functions run on the Workers runtime and do not host this JVM Spring Boot server directly; the compatible target is Workers + Containers, subject to Workers Paid/container entitlement.
- Next check: add the minimal Workers + Containers wrapper around the Java 21 image, document required secrets/config names without values, and verify only with a real account/deploy/request. Do not claim deployment success from a local Docker build.

#### F5 — staged Git state deletes `.gitignore` and exposes build/secrets risk

- Classification: `BUG`
- Evidence: `SOURCE ONLY`
- Priority: `P2` release hygiene/security risk.
- Confidence: high.
- Evidence: `git status --short` shows `D  .gitignore`; `target/` is staged as added after verification, `.env` is untracked, and `.dockerignore` is absent. The handoff said the implementation was untracked, but the current index contains a large staged set and generated build outputs.
- Impact: a future broad staging/commit can include build artifacts or the local environment file, and the deletion removes the repository's existing secret/build ignore rules. No `.env` contents were read or committed by this review.
- Next check: restore `.gitignore`, add a suitable `.dockerignore`, remove generated artifacts from the intended change set without touching unrelated user work, and re-check `git status` before any commit.

#### F6 — production validator contract is incomplete and not the observed fail-fast path

- Classification: `BUG`
- Evidence: `REPRODUCED LOCAL` for the startup path plus `SOURCE ONLY` for the missing property list.
- Priority: `P2` deployability.
- Confidence: high.
- Evidence: `ProductionConfigValidator` checks datasource URL/username and `supabase.url`/`supabase.anon-key`, but not the datasource password or the configured `supabase.jwks-uri`. README lists the password as required. A real `prod` start without env failed on unresolved `SPRING_DATASOURCE_URL` during `DataSource` bean creation before the validator's intended error was emitted.
- Impact: missing configuration is rejected, but diagnostics and the required-property contract are inconsistent; auth verification configuration is not guaranteed by the validator.
- Next check: define the exact production auth/database property contract, validate it before dependent beans, and test startup failures with sanitized messages.

### Boundary and release decision

- Local domain/application tests and package are green; they do not prove Supabase JWT behavior, PostgreSQL/Flyway execution against the real project, Docker image execution, or Cloudflare deployment.
- The main blocking issue is F1/F2. F3-F6 remain release blockers for a production deployment even though they are not all observable in the in-memory test suite.
- No source fix was made by this reviewer. No Supabase dashboard, secret, database, Cloudflare account, commit, push, or deploy was changed.

## Entry P3

- Handle: `P3-20260917-001`
- Prepared at: `2026-09-17 16:34:54 +07:00` (`2026-09-17 09:34:54 UTC`)
- Destination: Antigravity implementor
- Sent: no; native Computer Use still reports `apps: []`, so m must copy this prompt manually.
- Purpose: close the actual release blockers found in `REV-20260917-002`.

### Prompt

~~~text
PROMPT HANDLE: P3-20260917-001

Continue as Implementor for G:/phonghub. Read context.md and log1.md first. Treat Reviewer REV-20260917-002 as the current evidence. Do not edit the reviewer findings away.

Before editing:
- Record the actual branch, HEAD, and git status.
- Preserve context.md, log1.md, and unrelated user changes.
- Do not commit, push, merge, deploy, or print secrets.
- Do not use a demo identity, fallback ADMIN, X-User-Id, asUser, or client role as production authentication.

Goal
Make the main room/contract/maintenance MVP genuinely safe to run behind the requested Cloudflare Workers + Containers target. Do not expand into billing, meters, invoices, payments, refunds, OAuth, notifications, or a broad work-order system.

1. Fix production authentication — release blocker
- Keep LocalDemoAuthenticationAdapter only behind an explicit local/demo profile/property.
- In prod, wire a real Spring Security resource-server boundary for Supabase JWTs using configured issuer/JWKS and standard claims. Add the required security dependencies/configuration.
- Resolve verified JWT subject to the domain user/profile. Load role, status, must_change_password, and property scope from PostgreSQL; do not trust a client-supplied role.
- Requests without a valid bearer identity must be rejected. Production must never fall back to DEMO_ADMIN or in-memory data.
- Do not treat SUPABASE_ANON_KEY as JWT verification. Keep service/admin keys server-side only.
- Add tests for no token, malformed/invalid token, ignored X-User-Id/asUser, disabled/suspended user, and successful subject-to-domain-user mapping. If a real Supabase verification cannot be completed, fail the production profile closed and report PARTIAL_BLOCKED; do not claim DONE.

2. Implement the agreed auth vertical needed by the MVP
- Add the smallest Spring/API service boundary for username login through Supabase email/password, session/refresh/logout as needed by the chosen client flow, and forced first-login password change.
- ADMIN may manually reset TENANT/STAFF/TECHNICIAN passwords through a server-only Supabase Admin adapter. Generate a temporary password server-side, set must_change_password=true, return it once only to the authorized ADMIN, never persist or log it, and record an audit event if the current schema/port supports it.
- Return generic invalid-credential errors and RFC 7807 errors. Do not add public self-signup or forgot-password in this slice.
- Align public.users.id with Supabase auth.users.id or add an explicit, consistently enforced mapping. Update migration/repository code and tests accordingly.

3. Make production writes atomic
- Add a transaction boundary for maintenance create/resolve, contract activation/termination, and contract plus occupants synchronization. A second-write failure must roll back the first write.
- Keep validation before write for RESERVED-room maintenance and the active-contract/room-status invariant.
- Add fault-injection tests for second-write failure and verify no phantom ticket, room drift, contract drift, or occupant drift.

4. Fix release/deployment hygiene
- Restore the repository .gitignore and add .dockerignore. Do not include .env, target/, secrets, or local caches in a commit or Docker build context.
- Keep the Java 21 Dockerfile reproducible, non-root, linux/amd64-compatible, port 8080, and production-profile oriented. Do not run a Docker build against a context containing secrets.

5. Add the requested Cloudflare path
- Use Workers + Containers for this Spring Boot/Thymeleaf server; do not pretend it is a Pages Function.
- Add package.json, wrangler.jsonc/toml, Worker source using @cloudflare/containers, container binding, defaultPort 8080, required Durable Object migration/new_sqlite_classes configuration, and fetch forwarding for all app paths.
- Document Docker and npx wrangler deploy/containers list commands, required secret/config names only, and the fact that Containers require the appropriate Workers plan.
- Do not claim a real Cloudflare deploy or URL check without account access and an actual successful request. Report that boundary as UNVERIFIED/BLOCKED if unavailable.

Verification and handoff
- Run the full test suite and a real clean Maven package.
- Add/execute production-profile tests; run any available PostgreSQL/Flyway integration test without exposing secrets.
- Inspect git diff/status. No commit/push/deploy.
- Finish with exactly one of IMPLEMENTATION_DONE_READY_FOR_VERIFICATION, PARTIAL_BLOCKED, or HANDOFF_CONFLICT.
- Report actual HEAD/status, changed files, test/build results, auth/persistence/deployment evidence, and unresolved boundaries.
~~~

## Entry REV-20260917-003

- Handle: `REV-20260917-003`
- Reviewed at: `2026-09-17 19:01:06 +07:00`
- Request: Verify the implementor handoff and assess whether the project is sufficiently hexagonal; user clarified the mandatory rule: Spring Boot/Spring Security/JDBC/Flyway must own runtime and infrastructure, with strict Ports and Adapters organization.
- Role: Independent Reviewer. No source/config/migration/deployment fix was made. Only this log entry was appended.

### Evidence checked

- Source dependency scan: `src/main/java/com/phonghub/domain` and `src/main/java/com/phonghub/application` contain no Spring/JDBC/Supabase/HTTP imports. This part is consistent with Hexagonal Architecture.
- Full independent `./mvnw.cmd test`: `73` tests passed, `0` failures, `0` errors; started `2026-09-17T17:20:23.901+07:00`, ended `2026-09-17T17:20:38.366+07:00`.
- `git diff --check`: exit `0`.
- Current Git state is not clean despite the handoff claim: branch `feat/content`, HEAD `8c2c859948f2918f0a55d4baead29b800ba942f1`, many tracked source/target modifications and untracked auth/Cloudflare files; `log1.md` is modified.
- `npx wrangler@4.133.0 deploy --dry-run`: fails to bundle because workspace dependencies are not installed and warns `default_port` is unexpected in `wrangler.jsonc`.
- `npx wrangler@3.114.17 deploy --dry-run`: fails because the current `containers` array is incompatible with that Wrangler version; Wrangler reports update available to `4.133.0`.
- Official Cloudflare documentation confirms `defaultPort` belongs on the `Container` subclass and the Wrangler container config uses `class_name`/`image` plus a Durable Object binding/migration.

### Findings

#### F7 — inbound web adapters depend on a concrete outbound adapter

- Classification: `BUG`
- Evidence: `SOURCE ONLY`
- Priority: `P2`
- Confidence: high.
- `CurrentUserInterceptor`, `GlobalUiAdvice`, and `UserSwitchUiController` import `adapter.out.identity.LocalDemoAuthenticationAdapter` directly. `DataSeeder` also imports that adapter for IDs. This is adapter-to-adapter coupling and bypasses an application port.
- Impact: the inbound web layer and persistence demo seeding cannot be replaced independently; the hexagonal dependency direction is broken even though the core package scan is clean.
- Minimum next check/fix: move demo actor selection/fixtures behind an application capability port or a demo-only inbound adapter, and keep concrete wiring in `config` only.

#### F8 — `must_change_password` is carried but not enforced

- Classification: `BUG`
- Evidence: `SOURCE ONLY`; existing tests only assert the login response flag.
- Priority: `P1`
- Confidence: high.
- `DomainAuthenticationToken` stores the flag, but `ProductionSecurityConfig` permits every authenticated request and no filter/use-case policy reads the flag. A first-login user can therefore call business APIs with the temporary-password session.
- Impact: violates the agreed first-login restriction and the requirement to protect against already-issued JWTs.
- Minimum next check/fix: introduce a framework-neutral authenticated-user state/policy port, map it in the Spring Security adapter, and add a Spring inbound gate allowing only password change/logout while the flag is true. Add a MockMvc test for a `mustChangePassword=true` bearer token accessing a business endpoint.

#### F9 — admin provisioning loses the one-time temporary password

- Classification: `BUG`
- Evidence: `SOURCE ONLY`
- Priority: `P1`
- Confidence: high.
- `AuthService.adminCreateUser` generates a temporary password but returns only `User`; `AuthApiController` serializes that `User`. The admin never receives the generated credential.
- Impact: the newly provisioned account cannot complete the agreed first-login flow without an untracked out-of-band channel.
- Minimum next check/fix: return a dedicated application output DTO containing the user summary and one-time password; never persist or log the password; add controller and use-case tests.

#### F10 — production JWT configuration is not fail-closed

- Classification: `BUG`
- Evidence: `SOURCE ONLY`
- Priority: `P1`
- Confidence: high.
- `application-prod.yml` defaults `supabase.jwks-uri` to `https://example.supabase.co/...`; `ProductionSecurityConfig` repeats a hardcoded example fallback and only conditionally installs issuer validation. No audience validator is configured, and `ProductionConfigValidator` does not require JWKS/issuer/audience.
- Impact: production can validate against the wrong key set or omit required issuer/audience checks. This contradicts the selected `iss/sub/exp/audience` boundary.
- Minimum next check/fix: use Spring `@ConfigurationProperties` with fail-fast validation; require the actual project JWKS/issuer/audience; install issuer, timestamp, and audience validators; add wrong-issuer/wrong-audience tests.

#### F11 — Supabase adapter bypasses the Spring HTTP boundary

- Classification: `BUG`
- Evidence: `SOURCE ONLY`
- Priority: `P2`
- Confidence: high.
- `SupabaseIdentityProviderAdapter` constructs JDK `HttpClient` and an ad-hoc Jackson `ObjectMapper`; the config constructs it with `new ObjectMapper()`. This conflicts with the clarified Spring-only rule and makes Spring-managed timeouts, codecs, observability, and test replacement harder.
- Impact: infrastructure concerns leak as unmanaged implementation details and adapter tests require transport-level workarounds.
- Minimum next check/fix: expose a Spring-managed `RestClient`/codec configuration and inject it through the outbound port adapter; map provider statuses to typed port errors.

#### F12 — transaction tests verify rollback invocation, not rollback state

- Classification: `BUG`
- Evidence: `SOURCE ONLY`; `73/73` local tests pass.
- Priority: `P2`
- Confidence: high.
- `TransactionRollbackFaultInjectionTest` uses mocked repositories and only verifies `PlatformTransactionManager.rollback(...)`. `InMemoryPersistenceConfig` uses a no-op transaction manager, so no state can actually be restored in the local profile.
- Impact: the handoff claim “no phantom ticket/room drift” is not proven; a future repository implementation can leave partial state while tests remain green.
- Minimum next check/fix: add a real PostgreSQL/Testcontainers adapter test for each multi-write boundary, or a transaction-aware fake that snapshots/restores state; keep transaction ownership at the application-use-case decorator and remove duplicate repository-level transaction annotations where unnecessary.

#### F13 — external Supabase writes are not atomic with profile writes

- Classification: `BUG`
- Evidence: `SOURCE ONLY`
- Priority: `P2`
- Confidence: high.
- Admin reset/create calls Supabase first and then writes PostgreSQL; the Spring database transaction cannot roll back the remote Auth mutation. `IdentityProviderPort` has no compensation/idempotency/audit capability.
- Impact: provider success followed by DB failure can leave Auth and `public.users` inconsistent.
- Minimum next check/fix: define the MVP consistency policy explicitly and implement the smallest safe compensation/retry/audit path (for create, provider delete compensation or a pending provisioning state; for reset, a recoverable state and retry/audit record).

### Verdict

`PARTIAL / NOT READY FOR FINAL REVIEW`: the project has a valid hexagonal skeleton and the core is framework-free, but strict Spring + Hexagonal rules and core auth invariants are not satisfied. Do not treat `IMPLEMENTATION_DONE_READY_FOR_VERIFICATION` as release-ready.

### Implementor handoff boundary

- No source fix, commit, push, Supabase write, Cloudflare deploy, or secret read was performed.
- The next implementation should first resolve F7-F13, then run the full test/build plus real PostgreSQL/Flyway and Cloudflare dry-run checks. The Cloudflare live deployment remains `UNVERIFIED/BLOCKED` without account credentials and a successful request.

## Entry P5

- Handle: `P5-20260917-001`
- Prepared at: `2026-09-17 19:01:06 +07:00`
- Destination: Antigravity implementor
- Sent: no; prepared for manual copy because the user requested the prompt outside the app.
- Scope: strict Spring + Hexagonal Architecture correction after `REV-20260917-003`.

### Prompt

~~~text
PROMPT HANDLE: P5-20260917-001

Continue as Implementor for G:/phonghub. Read context.md and log1.md first. Treat REV-20260917-003 as independent evidence. Preserve unrelated user changes. Do not commit, push, merge, deploy, read/print secrets, or edit reviewer findings away.

Mandatory architecture rule
- This is a Spring Boot project. Use Spring Boot/Spring Security/Spring JDBC/Flyway and Spring-managed infrastructure consistently.
- Keep strict Hexagonal Architecture: domain/application depend only inward on domain types and ports; inbound web adapters do not import concrete outbound adapters; outbound adapters do not import other adapters; config is the only composition root.
- Domain/application must remain framework-free. Do not “fix” Hexagon by adding Spring annotations to the core.
- Replace ad-hoc JDK HttpClient/manual ObjectMapper wiring with Spring-managed RestClient/codec beans and typed outbound-port errors.

Required fixes, in this order
1. Remove concrete LocalDemoAuthenticationAdapter dependencies from CurrentUserInterceptor, GlobalUiAdvice, UserSwitchUiController, and DataSeeder. Split read-only CurrentUserPort from demo actor switching/fixture capabilities and wire implementations only in config.
2. Enforce must_change_password. Carry the state through a framework-neutral application identity/context port and add a Spring inbound gate that allows only change-password/logout while the flag is true. Add MockMvc coverage for a valid JWT with mustChangePassword=true calling a business endpoint.
3. Make admin provisioning return a dedicated application output containing the one-time temporary password. Never store or log it. Add use-case/controller tests.
4. Replace example JWKS fallback and @Value sprawl with validated Spring @ConfigurationProperties. Require actual JWKS URI, issuer, and configured audience; validate issuer, timestamp, and audience; add wrong-issuer/wrong-audience tests.
5. Separate invalid credentials from Supabase outage/timeout/5xx in IdentityProviderPort and map them to correct API errors. Keep hidden-email username login.
6. Prove transaction state rollback with PostgreSQL/Testcontainers adapter tests or a transaction-aware fake; do not claim rollback from Mockito verify(rollback). Keep transaction ownership at the use-case boundary and remove unnecessary duplicate repository transaction annotations.
7. Define and implement the smallest safe consistency path for Supabase Auth plus users writes: compensation/idempotency/pending state and audit event. Also align optional phone, migration constraints, refresh/logout, and audit behavior with context.md; do not expand into billing.

Verification
- Run .\mvnw.cmd test and a package/build check.
- Run npx wrangler@4 deploy --dry-run after installing the declared workspace dependencies; fix the containers schema/defaultPort issue.
- Re-run architecture scans and inspect git diff/status. Report exact evidence and unresolved PostgreSQL/Supabase/Cloudflare runtime boundaries.
- Finish with exactly one of IMPLEMENTATION_DONE_READY_FOR_VERIFICATION, PARTIAL_BLOCKED, or HANDOFF_CONFLICT. Do not claim release approval.
~~~

## Entry REV-20260917-006

- Handle: `REV-20260917-006`
- Reviewed at: `2026-09-17 19:39:57 +07:00` to `2026-09-17 19:50:23 +07:00`
- Request: Verify implementation quality after the implementor reported `IMPLEMENTATION_DONE_READY_FOR_VERIFICATION`.
- Role: Independent Reviewer. No source/config/migration/Supabase/Cloudflare account write was made. Only this log entry was appended.

### Evidence checked

- Branch: `feat/content`; HEAD: `8c2c859948f2918f0a55d4baead29b800ba942f1`.
- Independent `./mvnw test`: `80` tests passed, `0` failures, `0` errors, exit `0`.
- Independent `./mvnw package -DskipTests -B`: `BUILD SUCCESS`, exit `0`.
- Independent `npx wrangler deploy --dry-run`: Docker image build/binding packaging passed, exit `0`; this was not a live deploy or request check.
- Independent `npm exec tsc -- --noEmit`: failed, exit `1`, `src/worker.ts(13,51): error TS2554: Expected 1 arguments, but got 2.`
- `git diff --check`: exit `0` with line-ending warnings. The worktree remains dirty; the review did not clean or stage it.
- No real PostgreSQL/Flyway migration, Supabase Auth/JWKS call, or deployed Cloudflare runtime was available for this review.

### Findings

#### F14 — Cloudflare deployment can start the local demo profile

- Classification: `BUG`
- Evidence: `SOURCE ONLY`
- Priority: `P1` release/security blocker.
- Confidence: high.
- Evidence: `application.yml` enables demo by default; `DemoSecurityConfig` permits every request outside `prod`; in-memory persistence is selected by `@Profile("!prod")`; `src/worker.ts` has no `envVars` or `SPRING_PROFILES_ACTIVE`; the Docker entrypoint also has no production-profile default.
- Impact: if the Cloudflare container is started without an external profile/env injection, it can run unauthenticated in-memory demo data instead of PostgreSQL/Supabase production mode. The current Wrangler dry-run does not prove runtime environment propagation.
- Next check/fix: make production mode explicit and fail closed for a deployment target, and pass `SPRING_PROFILES_ACTIVE=prod` plus the required database/Supabase secrets through the Container `envVars`/Worker secrets path. Cloudflare documents that user-defined variables must be passed to the Container through `envVars` or container start options: https://developers.cloudflare.com/containers/examples/env-vars-and-secrets/.

#### F15 — Refresh flow is unreachable after access-token expiry

- Classification: `BUG`
- Evidence: `SOURCE ONLY` against the agreed refresh contract.
- Priority: `P1` core authentication flow.
- Confidence: high.
- Evidence: `/api/auth/refresh` is declared in `AuthApiController`, but `ProductionSecurityConfig` permits only `/api/auth/login` among auth endpoints and applies `.anyRequest().authenticated()`. `AuthService.refreshToken` also calls `currentUserPort.getCurrentUser()` before calling the provider, so an unauthenticated refresh request would still fail in the production adapter.
- Impact: a client cannot refresh using only a refresh token once the bearer access token is expired, which is the normal refresh boundary. Supabase documents refreshing when the access token is expired: https://supabase.com/docs/guides/auth/oauth-server/oauth-flows.
- Next check/fix: allow the refresh endpoint without an access-token authentication requirement and redesign the use case so it does not require `CurrentUserPort` before exchanging the refresh token; add a MockMvc test with no bearer header.

#### F16 — Admin create/reset can lose the one-time temporary password after a later failure

- Classification: `BUG`
- Evidence: `SOURCE ONLY`
- Priority: `P1` account-provisioning consistency.
- Confidence: high.
- Evidence: reset calls the remote provider, saves the domain profile, then writes the audit event; create compensates only when the profile save fails, then writes the audit event after that compensation window. Any profile/audit failure after the provider mutation leaves the remote password/user changed without a response containing the temporary password. `adminDeleteUser` also swallows compensation failures.
- Impact: an admin can receive an error while the account is already forced to use an unknown temporary password, or an orphan Supabase identity can remain. The local tests do not inject provider/database/audit failures across this boundary.
- Next check/fix: define a recoverable cross-system consistency policy (pending provisioning/outbox or explicit compensation/retry state), and add failure tests for provider success followed by profile and audit failure. Do not claim database transaction atomicity across Supabase.

#### F17 — Worker type-check is red even though Wrangler dry-run is green

- Classification: `BUG`
- Evidence: `REPRODUCED LOCAL`
- Priority: `P2` release quality gate.
- Confidence: high.
- Evidence: the declared installed `@cloudflare/containers@0.0.1` types expose `getContainer(binding)` while `src/worker.ts` calls `getContainer(env.CONTAINER, "phonghub-instance")`; `npm exec tsc -- --noEmit` fails at line 13. The deploy script has no typecheck script, and Wrangler dry-run does not catch this TypeScript error.
- Impact: the Cloudflare source is not type-safe/CI-ready; a successful dry-run is not sufficient evidence of a clean Worker build.
- Next check/fix: align the `@cloudflare/containers` package/API with the selected Wrangler version or use the installed API signature, add `typecheck` to the Worker verification script, and rerun it before release.

#### F18 — Strict Hexagonal boundary still has an inbound-port implementation in an outbound adapter

- Classification: `BUG`
- Evidence: `SOURCE ONLY`
- Priority: `P2` architecture contract.
- Confidence: high.
- Evidence: `LocalDemoAuthenticationAdapter` in `adapter.out.identity` imports and implements `application.port.in.DemoActorPort`. An outbound identity adapter is therefore implementing an inbound application port; this is not a composition-root-only wiring decision.
- Impact: the local demo actor control capability remains coupled to the wrong port direction, contrary to the explicitly strict Spring/Hexagonal rule. The domain/application framework-free scan is clean, but that scan does not prove all port semantics are correct.
- Next check/fix: split demo actor control from the inbound use-case namespace and keep the concrete adapter/wiring behind a clearly named demo-only capability port.

#### F19 — Production/deployment documentation is stale and incomplete

- Classification: `BUG`
- Evidence: `SOURCE ONLY`
- Priority: `P2` deployability/documentation contract.
- Confidence: high.
- Evidence: README still references the deleted `SupabaseAuthenticationAdapter`; its production required-variable list and Docker command omit `SUPABASE_SERVICE_ROLE_KEY`, `SUPABASE_JWKS_URI`, `SUPABASE_JWT_ISSUER`, and the configured audience, while the current production validator requires them.
- Impact: following the repository README can produce a production startup failure or an incompletely configured auth boundary.
- Next check/fix: update README and the Cloudflare deployment instructions from the actual current config, while keeping secret values out of Git.

### Verdict

`BUG / PARTIAL — NOT READY FOR RELEASE APPROVAL`.

The implementation is materially improved: the Java tests and package pass, the 80-test suite is green, production JWT checks and forced-password gating are present, and Wrangler can package the container. It still fails the quality/release gate because the Cloudflare Worker type-check is red, the deployment path is not fail-closed to production, refresh cannot work after token expiry, and cross-system user provisioning can lose the one-time credential. Real PostgreSQL/Flyway, Supabase, and Cloudflare runtime behavior remains `UNVERIFIED/BLOCKED`.

### Reviewer boundary

- No source fix, commit, push, Supabase dashboard change, database migration, or live Cloudflare deploy was performed.
- The worktree was left as found apart from generated build outputs and this log append.

## Entry REV-20260917-007

- Handle: `REV-20260917-007`
- Reviewed at: `2026-09-17 19:58:42 +07:00`
- Request: Prepare separate prompts to snapshot/push staging first, then handle blockers, after changing the deployment target to Cloudflare frontend + Render backend/container.
- Role: Independent Reviewer/Planner. No source/config/migration/Supabase/Cloudflare account write, commit, or push was made.

### Deployment configuration audit

- Current branch is `feat/content` at `8c2c859948f2918f0a55d4baead29b800ba942f1`; `origin/staging` remains at `831cf9c`.
- The repository has a Spring Boot + Thymeleaf application and no standalone frontend directory/package/config that can currently be deployed to Cloudflare Pages or a frontend Worker.
- `wrangler.jsonc` and `src/worker.ts` currently package and proxy the Spring backend container through Cloudflare Containers. That is not a Cloudflare frontend deployment and conflicts with the newly stated target unless the Worker is intentionally an edge proxy.
- There is no `render.yaml`. The Docker image is suitable as a starting point for Render, but the application currently hardcodes port `8080` while Render web services provide/use `PORT` (default `10000`) and require the service to bind to `0.0.0.0`.
- The Cloudflare Worker currently does not pass production profile/secrets into the container. This overlaps F14 and must not be considered solved by a dry-run.
- `npm exec tsc -- --noEmit` remains red at `src/worker.ts(13,51)` because the installed `@cloudflare/containers@0.0.1` type exposes a one-argument `getContainer` signature while the source passes two arguments.
- No `.env` contents were read or logged. No live Render, Cloudflare, Supabase, PostgreSQL, or deployed-runtime evidence was obtained.

### Classification and next decision

#### F20 — Cloudflare frontend target has no frontend artifact

- Classification: `NEEDS DECISION`
- Evidence: `SOURCE ONLY`
- Priority: `P1` deployment-scope blocker.
- Confidence: high; the current repository structure is directly observable.
- Impact: an implementor cannot truthfully configure Cloudflare frontend deployment without either extracting/building a separate frontend or redefining Cloudflare as DNS/CDN/edge proxy. Creating a new frontend is scope expansion and must not be silently inferred.
- M needs to choose: preserve the existing Thymeleaf UI and let Render serve it, with Cloudflare as edge/DNS; or explicitly authorize a separate frontend extraction/build for Cloudflare.

#### F21 — Render deployment configuration is missing and port/profile wiring is incomplete

- Classification: `BUG`
- Evidence: `SOURCE ONLY`
- Priority: `P1` deployment blocker.
- Confidence: high.
- Impact: a Render web service has no repository Blueprint, may probe the wrong port, and can start without an explicit production profile unless dashboard configuration is manually completed.
- Next check/fix: add a secret-free `render.yaml`, make Spring consume `${PORT:8080}`, set `SPRING_PROFILES_ACTIVE=prod`, configure `/health`, and document required Render/Supabase variables without committing values.

### Prompt handles prepared

- `PUSH-STAGING-20260917-001`: create explicit, separately reviewable baseline commits and push the current snapshot to `origin/staging` without force-push or staging unrelated files. This is a snapshot only and must not be called release-ready.
- `BLOCKERS-20260917-001`: after the staging push is confirmed, resolve F14-F21, align the Cloudflare/Render architecture, add the required config/tests/docs, and rerun the full local verification gates. If the missing frontend decision is not resolved, return `NEEDS DECISION`/`PARTIAL_BLOCKED` instead of inventing a frontend.

### Verdict

`NEEDS DECISION + BUG / CONFIGURATION NOT READY FOR DEPLOYMENT`.

The requested staging snapshot can be separated from blocker remediation. The blocker prompt must stop at the frontend boundary if no frontend artifact or explicit extraction scope is authorized; it may still make the Render backend configuration internally deployable and keep the strict Spring/Hexagonal boundary intact.

## Entry REV-20260917-008

- Handle: `REV-20260917-008`
- Reviewed at: `2026-09-17 19:59:30 +07:00`
- Clarification: the frontend will be built with React + TypeScript and deployed on Cloudflare; the Spring Boot backend remains a Docker service on Render.
- Effect: F20's product decision is resolved. The repository still has no React/TypeScript frontend artifact, so `BLOCKERS-20260917-001` must include creation of the smallest `frontend/` app/config needed for the agreed MVP, without rewriting backend domain/application code.
- Deployment boundary: frontend consumes a public `VITE_API_BASE_URL` pointing to Render; no Supabase service-role key, database credential, or other backend secret may enter the React bundle. Backend must configure CORS for the actual Cloudflare origin(s), keep auth verification and business authorization in Spring, and expose only the required API contract.
- Prompt update: use `PUSH-STAGING-20260917-001` unchanged for the baseline snapshot; use the revised `BLOCKERS-20260917-002` for React/TypeScript + Cloudflare frontend, Render Docker backend, and the previously recorded F14-F19 blockers.

## Entry PROMPT-20260917-009

- Handle: `PROMPT-20260917-009`
- Prepared at: `2026-09-17 20:02:34 +07:00`
- Result: prompts are ready for manual handoff; no prompt was sent to Antigravity by this turn and no source/commit/push/live deployment was performed.
- `PUSH-STAGING-20260917-001`: selective backend-only baseline commit(s), Maven build gate, push `HEAD:staging` without force, then a separate log commit/push. It explicitly excludes the known-red Worker/frontend work and does not claim release readiness.
- `BLOCKERS-20260917-002`: after verifying the staging SHA, build the React + TypeScript frontend for Cloudflare Pages, configure the Spring Docker backend for Render, remove the misleading Cloudflare backend-container path, resolve F14-F19, add CORS/auth/error/edge tests, and verify all local gates. It must return `PARTIAL_BLOCKED` if any required check or deployment decision remains unresolved.

## Entry PUSH-STAGING-20260917-001

- Role: Implementor.
- Timestamp: `2026-09-17 20:14:16 +07:00`.
- Branch: `feat/content`.
- Snapshot HEAD before the documentation commit: `25670523926b6103503ce1d19b1b5aee7cafec8a`.
- Snapshot commit: `feat: snapshot backend mvp for staging` (`25670523926b6103503ce1d19b1b5aee7cafec8a`).
- Snapshot status: staging baseline only; not release-ready.

### Files committed in the backend snapshot (74 paths)

- `.dockerignore`
- `.gitignore`
- `docs/staging-backend-allowlist.txt`
- `pom.xml`
- `src/main/java/com/phonghub/PhongHubApplication.java`
- `src/main/java/com/phonghub/adapter/in/security/DomainAuthenticationToken.java`
- `src/main/java/com/phonghub/adapter/in/security/PasswordChangeGate.java`
- `src/main/java/com/phonghub/adapter/in/security/SecurityCurrentUserAdapter.java`
- `src/main/java/com/phonghub/adapter/in/security/SupabaseJwtAuthenticationConverter.java`
- `src/main/java/com/phonghub/adapter/in/web/CurrentUserInterceptor.java`
- `src/main/java/com/phonghub/adapter/in/web/GlobalUiAdvice.java`
- `src/main/java/com/phonghub/adapter/in/web/RestExceptionHandler.java`
- `src/main/java/com/phonghub/adapter/in/web/UserSwitchUiController.java`
- `src/main/java/com/phonghub/adapter/in/web/api/AuthApiController.java`
- `src/main/java/com/phonghub/adapter/in/web/api/ContractApiController.java`
- `src/main/java/com/phonghub/adapter/in/web/api/MaintenanceApiController.java`
- `src/main/java/com/phonghub/adapter/in/web/api/PropertyApiController.java`
- `src/main/java/com/phonghub/adapter/in/web/api/RoomApiController.java`
- `src/main/java/com/phonghub/adapter/in/web/api/dto/AdminCreatedUserResponse.java`
- `src/main/java/com/phonghub/adapter/in/web/api/dto/AdminPasswordResetResponse.java`
- `src/main/java/com/phonghub/adapter/in/web/api/dto/ContractResponse.java`
- `src/main/java/com/phonghub/adapter/in/web/api/dto/MaintenanceTicketResponse.java`
- `src/main/java/com/phonghub/adapter/in/web/api/dto/PropertyResponse.java`
- `src/main/java/com/phonghub/adapter/in/web/api/dto/RoomResponse.java`
- `src/main/java/com/phonghub/adapter/out/audit/InMemoryAuditAdapter.java`
- `src/main/java/com/phonghub/adapter/out/audit/PostgresAuditAdapter.java`
- `src/main/java/com/phonghub/adapter/out/identity/LocalDemoAuthenticationAdapter.java`
- `src/main/java/com/phonghub/adapter/out/identity/LocalDemoIdentityProviderAdapter.java`
- deleted `src/main/java/com/phonghub/adapter/out/identity/SupabaseAuthenticationAdapter.java`
- `src/main/java/com/phonghub/adapter/out/persistence/inmemory/DataSeeder.java`
- `src/main/java/com/phonghub/adapter/out/persistence/inmemory/InMemoryContractRepository.java`
- `src/main/java/com/phonghub/adapter/out/persistence/inmemory/InMemoryMaintenanceTicketRepository.java`
- `src/main/java/com/phonghub/adapter/out/persistence/inmemory/InMemoryRoomRepository.java`
- `src/main/java/com/phonghub/adapter/out/persistence/inmemory/InMemoryTenantRepository.java`
- `src/main/java/com/phonghub/adapter/out/persistence/inmemory/InMemoryUserRepository.java`
- `src/main/java/com/phonghub/adapter/out/persistence/postgres/PostgresUserRepository.java`
- `src/main/java/com/phonghub/adapter/out/supabase/auth/SupabaseIdentityProviderAdapter.java`
- `src/main/java/com/phonghub/application/port/in/AuthTokenResponse.java`
- `src/main/java/com/phonghub/application/port/in/AuthUseCase.java`
- `src/main/java/com/phonghub/application/port/in/DemoActorPort.java`
- `src/main/java/com/phonghub/application/port/out/AuditPort.java`
- `src/main/java/com/phonghub/application/port/out/CurrentUser.java`
- `src/main/java/com/phonghub/application/port/out/CurrentUserPort.java`
- `src/main/java/com/phonghub/application/port/out/DemoFixturePort.java`
- `src/main/java/com/phonghub/application/port/out/IdentityProviderPort.java`
- `src/main/java/com/phonghub/application/port/out/UserRepositoryPort.java`
- `src/main/java/com/phonghub/application/service/AuthService.java`
- `src/main/java/com/phonghub/config/AudienceValidator.java`
- `src/main/java/com/phonghub/config/DemoSecurityConfig.java`
- `src/main/java/com/phonghub/config/InMemoryPersistenceConfig.java`
- `src/main/java/com/phonghub/config/PhongHubConfiguration.java`
- `src/main/java/com/phonghub/config/PostgresPersistenceConfig.java`
- `src/main/java/com/phonghub/config/ProductionConfigValidator.java`
- `src/main/java/com/phonghub/config/ProductionSecurityConfig.java`
- `src/main/java/com/phonghub/config/SnapshottingTransactionManager.java`
- `src/main/java/com/phonghub/config/SupabaseProperties.java`
- `src/main/java/com/phonghub/config/TransactionalContractUseCase.java`
- `src/main/java/com/phonghub/config/TransactionalMaintenanceUseCase.java`
- `src/main/java/com/phonghub/config/WebMvcConfig.java`
- `src/main/java/com/phonghub/domain/exception/AccountDisabledException.java`
- `src/main/java/com/phonghub/domain/exception/IdentityProviderUnavailableException.java`
- `src/main/java/com/phonghub/domain/exception/InvalidCredentialsException.java`
- `src/main/java/com/phonghub/domain/exception/PasswordChangeRequiredException.java`
- `src/main/java/com/phonghub/domain/model/User.java`
- `src/main/resources/application-prod.yml`
- `src/main/resources/db/migration/V3__align_phone_and_add_audit_log.sql`
- `src/main/resources/templates/error.html`
- `src/test/java/com/phonghub/adapter/in/web/ProductionAuthSecurityTest.java`
- `src/test/java/com/phonghub/application/AdditionalOccupantAccessTest.java`
- `src/test/java/com/phonghub/application/AuthServiceUnitTest.java`
- `src/test/java/com/phonghub/application/MaintenanceNoPartialWriteTest.java`
- `src/test/java/com/phonghub/application/RoomContractInvariantTest.java`
- `src/test/java/com/phonghub/config/ProductionConfigValidatorTest.java`
- `src/test/java/com/phonghub/config/TransactionRollbackFaultInjectionTest.java`

The exact staging delta is also recorded in `docs/staging-backend-allowlist.txt`.

### Excluded from the snapshot

- `.env` and all secret-bearing files/values; `.env` was not read or staged.
- `target/**`, `node_modules/**`, and `.wrangler/**` generated/dependency artifacts.
- `package.json` and `package-lock.json` for the Worker/Cloudflare dependency scope.
- `src/worker.ts`, `tsconfig.json`, and `wrangler.jsonc`; Worker typecheck is a known blocker.
- `log1.md`; intentionally reserved for this separate documentation commit.
- `README.md`, `context.md`, `docs/api-spec.yaml`, and any path outside the allowlist.
- No React frontend tree exists in this backend snapshot.

### Verification

- `./mvnw test` equivalent on Windows: `./mvnw.cmd test` — PASS, exit `0`; 80 tests, 0 failures, 0 errors, 0 skipped.
- `./mvnw package -DskipTests -B` equivalent on Windows: `./mvnw.cmd package -DskipTests -B` — PASS, `BUILD SUCCESS`, exit `0`.
- Worker blocker check: `npm exec tsc -- --noEmit` — FAIL, exit `1`; `src/worker.ts(13,51): error TS2554: Expected 1 arguments, but got 2.` This blocker was recorded only and not fixed in this snapshot.
- Cached diff audit — PASS: 74 staged paths matched the allowlist; no forbidden staged paths, secret-pattern hits, or `git diff --cached --check` errors.

### Push result

- Verified remote before push: `origin/staging` was `831cf9c7cb7bb917145dbccf318989ec201abadc`; it was an ancestor of the snapshot HEAD.
- Command: `git push origin HEAD:staging`.
- Result: PASS, non-force push; `831cf9c..2567052` and remote `staging` became `25670523926b6103503ce1d19b1b5aee7cafec8a`.
- No live deploy was performed.
