Started at: 2026/10/12 15:02:00
Finished at: 2026/10/12 15:02:00
Total time: 0 minutes
---

# Requirements: Identity & Access

## Source and Scope

- **Vision:** [2026-10-12-vision.md](./2026-10-12-vision.md)
- **Vision review:** [2026-10-12-vision-review.md](./2026-10-12-vision-review.md)
- **Parent architecture:** [POS Platform Architecture](../pos-platform/2026-10-12-architecture.md)
- **Parent requirements:** [POS Platform Requirements](../pos-platform/2026-10-12-requirements.md)
- **Parent contracts:** [POS Platform Contracts](../pos-platform/2026-10-12-contracts.md)
- **Original idea:** [idea.md](../../../idea.md)

### Priority and Acceptance Boundary

- **P0** is required for the MVP/demo and must have automated acceptance coverage.
- **P1** must have a clear boundary and compatible extension point, but is not required to block the P0 identity slice.
- P0/P1 requirements in this document are for the `identity-access` bounded context. Order, catalog, payment, inventory, loyalty, shift, reporting and detailed KDS permissions remain owned by their respective services.
- The selected approach is **Cognito authentication + Identity DB authorization**. Cognito is not the source of truth for tenant membership, role or branch scope.

## Actors and Terminology

| Term | Definition |
|---|---|
| Platform admin | Protected platform-level identity or bootstrap job allowed to create a tenant and first owner. It is not a normal tenant role. |
| Owner | Tenant-wide administrator. Can manage all branches and all employee membership types in the tenant. |
| Manager | Branch administrator. Can manage only `CASHIER` and `KITCHEN` memberships in assigned branches. |
| Cashier | Operational user for cashier/order workflows in assigned branches. |
| Kitchen | Operational user for future KDS workflows in assigned branches. KDS permissions are out of scope for this module. |
| Tenant | Restaurant chain/account boundary. Every tenant-owned record and authorization decision is tenant-scoped. |
| Branch | Operational location owned by exactly one tenant. |
| Membership | A user-to-tenant/branch assignment with one effective role and lifecycle status. |
| Active branch | The branch selected by the client for the current request context. It is a hint that the server must revalidate. |
| POS device | Registered physical/browser POS identity bound to one branch for P1 quick switch. |
| Effective principal | The authenticated Cognito subject, or the P1 cashier operator selected by PIN within a valid authenticated POS context. |

## Functional Requirements

### Use Cases

| ID | Priority | Actor | Use case | System response |
|---|---|---|---|---|
| UC-IA-01 | P0 | Platform admin/bootstrap job | Create a tenant and first owner | Creates the tenant and an owner invitation/membership through a protected flow; records actor and correlation metadata. |
| UC-IA-02 | P0 | Owner/Manager | Invite an employee by email | Normalizes email, validates actor scope and target role/branch, creates an `INVITED` membership idempotently, and dispatches a Cognito invitation. |
| UC-IA-03 | P0 | Invited employee | Complete Cognito invitation and sign in | Cognito authenticates the user; the service maps the Cognito `sub` to membership records and activates eligible invitations. |
| UC-IA-04 | P0 | Owner/Manager | List employees in an authorized scope | Returns only tenant/branch memberships visible to the actor, with cursor/offset behavior defined in Contracts. |
| UC-IA-05 | P0 | Owner | Create/update/archive a branch | Creates or changes a branch belonging to the owner tenant; archive is a soft state transition and is audited. |
| UC-IA-06 | P0 | Owner/Manager | Read branch context | Owner can read all tenant branches; manager can read assigned branches only. |
| UC-IA-07 | P0 | Owner | Assign or change employee role/branch | Validates the target scope, role transition and uniqueness; persists the new membership version and publishes an authorization-change event. |
| UC-IA-08 | P0 | Manager | Manage assigned branch employees | May invite, update, suspend or revoke only `CASHIER`/`KITCHEN` memberships in assigned branches. Cannot create/update `OWNER`/`MANAGER` or alter tenant-wide scope. |
| UC-IA-09 | P0 | Owner/Manager | Suspend or revoke access | Changes membership status, immediately invalidates the membership for subsequent protected requests, and emits audit/security records. |
| UC-IA-10 | P0 | Any employee | Resolve current identity and permissions | `GET /me` returns the authenticated subject, authorized tenant/branch memberships, role and server-derived permissions; it does not trust client-supplied role claims. |
| UC-IA-11 | P0 | Any employee | Select a tenant/active branch | Client selects from memberships returned by the service; every request carrying context is revalidated against the current membership state. |
| UC-IA-12 | P0 | Downstream service | Authorize a tenant/branch request | Service or shared authorization adapter checks JWT validity, membership status, role, tenant ownership and branch scope before data access. |
| UC-IA-13 | P0 | Any employee | Attempt unauthorized access | Returns the standard `401`/`403`/scoped `404` response without leaking another tenant/branch’s data and records a security audit event for denied sensitive actions. |
| UC-IA-14 | P1 | Authenticated POS user | Register or activate a POS device | Associates a device with one branch through an authenticated flow; device status and owner/auditor are recorded. |
| UC-IA-15 | P1 | Cashier | Set/reset own POS PIN | Sets a six-digit PIN through an authenticated session; only a strong hash is stored and the raw PIN is never returned or logged. |
| UC-IA-16 | P1 | Authenticated POS user | Quick-switch to a cashier | Requires an already authenticated POS session, a registered device, matching active branch and valid cashier PIN; returns a bounded cashier operator context without issuing a replacement Cognito identity. |
| UC-IA-17 | P1 | Owner/Manager | Revoke or lock a POS device/PIN | Disables the device or cashier PIN, clears active quick-switch context and writes a security audit record. |

### Authorization Matrix

The matrix is enforced server-side. UI route guards are usability only and are not security controls.

| Capability | Platform admin | Owner | Manager | Cashier | Kitchen |
|---|---:|---:|---:|---:|---:|
| Bootstrap tenant/first owner | Yes | No | No | No | No |
| Read own `/me` and authorized memberships | Yes | Yes | Yes | Yes | Yes |
| Read all tenant branches | Protected platform scope | Yes | No | No | No |
| Read assigned branch | N/A | Yes | Yes | Yes | Yes |
| Create/update/archive branch | N/A | Yes | No | No | No |
| Invite `OWNER`/`MANAGER` | N/A | Yes | No | No | No |
| Invite `CASHIER`/`KITCHEN` | N/A | Yes | Yes, assigned branch only | No | No |
| Change owner/manager membership | N/A | Yes | No | No | No |
| Change cashier/kitchen membership | N/A | Yes | Yes, assigned branch only | No | No |
| Suspend/revoke cashier/kitchen | N/A | Yes | Yes, assigned branch only | No | No |
| Suspend/revoke owner/manager | N/A | Yes, subject to last-owner rule | No | No | No |
| Use business service permissions | N/A | Tenant-wide | Assigned branches | Assigned branches | Assigned branches |
| Register/revoke POS device | N/A | Yes | Yes, assigned branch only | No | No |
| Set own cashier PIN | N/A | If also assigned cashier membership | No | Yes | No |

Rules:

1. An owner cannot remove or suspend the last active owner without a protected tenant transfer/bootstrap flow.
2. A manager cannot grant a role or branch scope equal to or greater than the manager’s own authority.
3. A user may have multiple memberships; within one tenant/branch there is at most one effective role for that user.
4. A user may hold different roles in different branches. Role and branch are evaluated from the selected membership, not from a global user role.
5. A suspended or revoked membership cannot be selected as an active branch membership or used for downstream authorization.
6. A client-provided `tenantId`, `branchId`, role, permission or employee ID is never evidence of ownership.

## Business Rules and State Machines

### Tenant and Branch

- A tenant is created only by the protected platform bootstrap flow; normal tenant users cannot create a second tenant through the public API.
- Every branch has exactly one immutable `tenantId`.
- Branch codes are unique within a tenant and normalized before comparison.
- Branches use `ACTIVE` and `ARCHIVED` states. Archiving prevents new operational assignments and new business activity but preserves historical references and audit data.
- Branch archive is not a hard delete in P0.
- Owner reads/writes are tenant-wide; manager reads only assigned active or historical branches allowed by policy; cashier/kitchen read only their current assigned branch context.

### Employee and Membership Lifecycle

Membership states are:

```text
INVITED → ACTIVE → SUSPENDED → ACTIVE
ACTIVE → REVOKED
SUSPENDED → REVOKED
INVITED → REVOKED
```

- `INVITED` means the Identity DB membership exists but the employee has not completed the Cognito invitation/first authenticated activation.
- `ACTIVE` means Cognito subject mapping and membership authorization are usable.
- `SUSPENDED` is reversible by an authorized owner or manager within scope.
- `REVOKED` is terminal for that membership record. Re-invitation creates a new membership history record rather than silently rewriting the revoked record.
- Cognito user lifecycle and membership lifecycle are reconciled by an adapter/job and by the authenticated activation path; neither external state alone grants access.
- Email is the invitation identifier. Email comparison is normalized and case-insensitive, while the Cognito `sub` is the stable subject identifier after activation.
- Invitation commands are idempotent. The same idempotency key and request hash return the same result; a different payload with the same key returns `duplicate_request`.
- Concurrent invite, role-change and branch-assignment commands use optimistic version checks and return `conflict` on stale writes.
- Duplicate active/invited memberships for the same tenant, user and branch are rejected. Historical revoked memberships remain auditable.

### Authentication and Authorization Context

- Cognito owns login, password policy, invitation delivery, token issuance and token validity.
- API Gateway and each Spring Boot service validate JWT issuer, signature, audience, expiry and required subject claims.
- Identity DB owns tenant, branch, membership status, role and effective permission resolution.
- `GET /me` is the canonical public read model for the current user’s authorized memberships.
- `X-Active-Branch-Id` is optional when an operation is tenant-wide and required when a branch-scoped operation needs an active branch. The header is always checked against current membership state.
- If a user has memberships in multiple tenants, the context must include an explicit tenant selection bound to a membership; no implicit tenant fallback is allowed. The exact wire representation is defined in Contracts.
- A valid JWT with a revoked/suspended membership is not sufficient for access. Authorization is rechecked against the current Identity DB state for every protected request or through an invalidated, bounded authorization cache that preserves the next-request revoke guarantee.
- Permission sets are server-derived from role and scope. Downstream services may consume the versioned authorization context but must not query Identity DB directly.

### PIN Quick Switch (P1)

- PIN quick switch is allowed only inside an already authenticated Cognito POS session; it is not a login replacement and does not create a Cognito JWT.
- A POS device is registered to exactly one branch at a time and has an active/revoked lifecycle.
- Only a target membership with role `CASHIER`, status `ACTIVE`, and the same tenant/branch as the registered device can be selected.
- A successful switch changes the effective operator to the target cashier for a short-lived POS context. The effective permissions are never broader than the base authenticated session and are revalidated server-side.
- Default PIN is exactly six numeric digits. PIN material is hashed with an approved password-hashing algorithm such as Argon2id or bcrypt; plaintext and reversible encryption are prohibited.
- PIN attempts are rate-limited by device, target membership and source context. Repeated failures cause temporary lockout; thresholds and lockout duration are configuration, not client-controlled values.
- PIN reset, device revocation, membership suspension/revocation and branch reassignment invalidate the active quick-switch context.
- PIN success, failure, lockout, reset and device revocation are auditable without recording the raw PIN.

## Integration Requirements

| ID | Integration | Requirement |
|---|---|---|
| INT-IA-01 | Amazon Cognito | Use OAuth2/OIDC login and JWT verification. Cognito invitation is initiated through an adapter with bounded retry and idempotency. |
| INT-IA-02 | API Gateway | Gateway validates/rate-limits/propagates correlation context; each service repeats JWT and authorization-context validation. |
| INT-IA-03 | Downstream services | Consume a versioned tenant/branch/role/permission context; do not access Identity DB directly. |
| INT-IA-04 | EventBridge + SQS | Publish versioned tenant/branch/membership/authorization-change events through an outbox. Consumers deduplicate by event ID and support retry/DLQ. |
| INT-IA-05 | Audit service | Sensitive management and security events are emitted with actor, effective principal, tenant, branch, target, action, outcome, correlation ID and redacted before/after data. |
| INT-IA-06 | Secrets Manager | Cognito client credentials, database credentials, signing/config secrets and integration secrets are injected at runtime; no secrets in source or logs. |
| INT-IA-07 | Local development | Provide a Cognito/email adapter fixture or local stub without changing the public membership and authorization contracts. |

## API-Level Requirements

The exact schemas and endpoint contracts are deferred to Phase 4, but every implementation must satisfy these boundaries:

| Area | Requirement |
|---|---|
| Base paths | Public APIs use `/api/v1`; service-to-service APIs use `/internal/v1`; provider callbacks, if needed, use `/callbacks/v1`. |
| Authentication | Protected public APIs require `Authorization: Bearer <Cognito JWT>`. |
| Context | Use `X-Correlation-Id`; use `X-Active-Branch-Id` only as a server-validated branch selector. Never accept role/tenant ownership from request body alone. |
| Mutations | Invitation, branch, membership, device and PIN mutations require `Idempotency-Key` when retryable and use optimistic versioning for updates. |
| Validation | Reject malformed email, role, branch, device, PIN and version inputs with the standard `validation_error` envelope. |
| Authorization | Missing/invalid JWT returns `401`; authenticated but out-of-scope returns `403`; absent resource inside the caller’s authorized scope returns `404`; stale mutation returns `409`. |
| Rate limiting | Invitation, activation, PIN verification, PIN reset and device registration have stricter actor/device/tenant limits and return `429` with retry metadata. |
| Pagination | Employee, branch and audit collections use the parent collection pagination conventions; filters are applied after tenant/branch authorization and before pagination. |
| PII | Responses expose only the minimum employee profile required by the caller; email/phone are masked where a full value is not needed. Tokens, raw PIN and provider secrets never appear in responses. |
| Error leakage | Error responses do not reveal whether an unauthorized caller guessed a resource belonging to another tenant/branch. |

## Events and Audit Requirements

### Domain/Integration Event Families

The following event families are required as versioned contracts when downstream consumers need them:

- `TenantCreated`
- `BranchCreated`, `BranchUpdated`, `BranchArchived`
- `MembershipInvited`, `MembershipActivated`, `MembershipRoleChanged`, `MembershipScopeChanged`, `MembershipSuspended`, `MembershipRevoked`
- `AuthorizationContextChanged`
- `PosDeviceRegistered`, `PosDeviceRevoked`, `PosPinChanged`, `PosQuickSwitchSucceeded`, `PosQuickSwitchDenied`

Every event includes at least `eventId`, `eventType`, `eventVersion`, `occurredAt`, `tenantId`, nullable/required `branchId` according to scope, `aggregateType`, `aggregateId`, `correlationId`, `causationId`, `producer` and a redacted payload.

### Audit Record Requirements

- Audit records are append-only and tenant-scoped; security events for invalid/denied requests include enough context for investigation without exposing secrets.
- Management audit records include actor Cognito subject, effective principal if different, action, target, tenant/branch scope, outcome, reason, correlation ID, request ID, timestamp and redacted before/after values.
- Raw JWTs, access/refresh tokens, plaintext PINs, passwords, Cognito client secrets and provider credentials are never logged or stored in audit payloads.
- Business mutation and membership authorization events use an outbox so a committed state change is not silently disconnected from its event.
- Audit query permissions follow the parent system’s audit boundary; this service owns event production and identity-sensitive fields, while Audit service owns the append-only query store.

## Non-Functional Requirements

> These are MVP/demo targets aligned with the parent POS requirements, not a production SLA.

| Category | Requirement | Target/Acceptance |
|---|---|---|
| Performance | Resolve `/me` including current memberships and permissions | p95 ≤ 300 ms under the demo profile, excluding Cognito’s external login latency. |
| Performance | Protected authorization decision | p95 ≤ 100 ms when Identity DB is healthy; no unbounded downstream call chain. |
| Performance | Invitation command | Return persisted command result within p95 ≤ 500 ms; email/Cognito delivery may complete asynchronously. |
| Consistency | Revoke/suspend effect | The first protected request after the membership transaction commits is denied; stale JWT expiry is not a prerequisite. |
| Consistency | Membership mutation | Role/branch changes use optimistic versioning and are atomic within Identity DB. |
| Availability | Identity API failure behavior | No request may silently fall back to client-provided role/branch claims when authorization state is unavailable; fail closed for protected actions. |
| Security | Authentication | Verify JWT signature, issuer, audience, expiry and subject; reject algorithm/key/configuration mismatch. |
| Security | Authorization | Enforce RBAC and tenant/branch ownership at API/service/repository boundaries; test BOLA/IDOR, cross-tenant and cross-branch access. |
| Security | Credential/PIN safety | No plaintext secret/PIN persistence; rate limits and temporary lockout are mandatory for invitation and PIN abuse paths. |
| Security | Data protection | Mask employee PII in logs and non-essential responses; use runtime secret injection and TLS. |
| Auditability | Sensitive actions | 100% of branch, membership, role, suspension, revocation, device and PIN security mutations emit audit/outbox records. |
| Observability | Request tracing | Structured JSON logs carry request ID, correlation ID, actor subject hash/opaque ID, tenant and branch context; sensitive values are redacted. |
| Observability | Metrics | Track invitation failures, activation lag, auth denial, membership mutation conflicts, Cognito dependency errors, PIN failures/lockouts, outbox lag and event DLQ depth. |
| Reliability | Idempotency | Replay of invitation/membership/device/PIN mutation commands cannot create duplicate active memberships or duplicate side effects. |
| Recovery | Dependency outage | Cognito/email outage leaves a durable `INVITED` command state for retry/reconciliation; it never activates access without a valid Cognito subject. |
| Scalability | API/worker | Stateless API layer; invitation/event workers have bounded concurrency and can scale independently. |
| Testing | Automated coverage | Unit, repository integration, Cognito adapter, API contract, E2E, security negative, concurrency and rate-limit/lockout tests are required. |

## Data and Consistency Requirements for Architecture Phase

The Architecture phase must design, at minimum, the following data ownership and access patterns:

| Aggregate/table family | Ownership and required behavior |
|---|---|
| `tenants` | Tenant identity, lifecycle, owner invariant and audit timestamps. |
| `branches` | Tenant-owned branch profile, normalized code, `ACTIVE/ARCHIVED` lifecycle and tenant-scoped uniqueness. |
| `users` or Cognito subject mapping | Stable Cognito `sub`, normalized invitation email, minimal profile and reconciliation status; no password storage. |
| `memberships` | Tenant + user + optional branch + one role + lifecycle status + optimistic version; active/invited uniqueness enforced. |
| `pos_devices` (P1) | Device identity, branch binding, lifecycle, registration metadata and last-seen/revocation fields without browser secrets in plaintext. |
| `pos_pins` (P1) | Membership/device association, password hash metadata, lockout counters/timestamps and rotation/revocation state; no raw PIN. |
| `outbox` | Atomic publication of membership/branch/device authorization events with retry metadata. |
| `audit/security event projection` | Append-only event shape or producer contract; query ownership remains with Audit service. |

Database requirements:

1. Every tenant/branch-scoped query must include explicit authorization scope in the repository/service boundary; middleware-only filtering is insufficient.
2. Cross-service foreign keys and runtime SQL joins are prohibited.
3. IDs use the parent UUID v7 convention unless Architecture explicitly approves another strategy.
4. All new columns require descriptive database comments/remarks in migration artifacts, following the `database-design` skill.
5. Expected MVP scale does not require partitioning for ordinary identity tables; audit/outbox growth and retention must be evaluated in Architecture.
6. Migrations must support forward deployment, rollback/repair strategy and deterministic seed data for local/demo environments.

## Acceptance Criteria

### P0 Authentication and Membership

1. Given an invited email, when an authorized owner/manager submits the same invitation command twice with the same idempotency key, then only one active invitation/membership workflow exists and the same result is returned.
2. Given a user completes Cognito invitation, when the user calls `/me` with a valid JWT, then the service returns the matching active tenant/branch membership and server-derived role/permissions.
3. Given a user has roles in two branches, when the user selects branch A then branch B, then each protected request is authorized with the selected branch’s role and membership only.
4. Given a manager assigned to branch A, when the manager tries to invite or mutate an employee in branch B, then the request is rejected and no state/event is created.
5. Given a manager tries to create or promote an `OWNER`/`MANAGER`, then the request is rejected regardless of request body values or UI state.
6. Given an owner archives a branch, then new membership assignment and branch-scoped operations are rejected while historical references remain readable according to policy.
7. Given a membership is suspended or revoked, when the next protected request is made with the old but otherwise valid Cognito JWT, then access is denied without waiting for token expiry.
8. Given a caller changes a path/header/body tenant or branch ID to another authorized-looking value, then the server never returns or mutates data outside the caller’s current validated scope.
9. Given two concurrent role/branch updates use the same membership version, then exactly one succeeds and the other receives a conflict requiring reload.
10. Given Cognito/email is unavailable during invitation, then the membership command remains durable and retryable without granting access or creating a duplicate membership.

### P1 PIN Quick Switch

11. Given a registered device and authenticated POS session, a valid active cashier PIN for the same branch creates a short-lived cashier context without issuing a Cognito token.
12. Given a PIN targets another branch, suspended/revoked cashier, manager or kitchen membership, then quick switch is rejected.
13. Given repeated invalid PIN attempts, then rate limiting/temporary lockout activates and no raw PIN is logged or returned.
14. Given a device or cashier membership is revoked, then any active quick-switch context becomes unusable on the next request.
15. Given a manager or owner uses quick switch to a cashier, then the effective context cannot grant permissions broader than the cashier role.

### Audit and Operations

16. Every successful or rejected branch/membership/role/suspend/revoke/PIN/device mutation has actor, effective principal, scope, target, outcome and correlation ID available in the audit path.
17. Security tests prove no cross-tenant, cross-branch, role-escalation, IDOR, stale-revoke or secret/PII leakage path passes.

## Open Decisions Carried to Architecture and Contracts

1. Exact tenant-selection wire format when one Cognito subject has memberships in multiple tenants.
2. Identity authorization lookup strategy that meets the next-request revoke guarantee without introducing a stale cache.
3. PostgreSQL schema, indexes, partial uniqueness constraints, migration tool and outbox delivery implementation.
4. Cognito adapter behavior for invitation retry, first-login activation and external user deletion/disablement.
5. Exact API schemas, pagination fields, concurrency headers and event payloads.
6. POS device registration proof, browser/device identity strategy and short-lived quick-switch context representation.
7. Configurable invitation/PIN rate-limit thresholds and lockout durations for demo versus production hardening.

## Domain Skills Referenced

- **spec-reviewer:** converted Vision warnings into explicit requirement coverage and acceptance criteria.
- **api-design:** preserved API-first REST/OpenAPI conventions, status/error semantics, idempotency, pagination, authentication and versioning boundaries.
- **database-design:** defined Identity DB ownership, membership uniqueness/state, consistency, migration remarks, indexing/partitioning evaluation and outbox requirements.
- **log-processing:** defined structured correlation context, audit/observability fields and sensitive-data redaction requirements; no code logging changes were performed.

## Approval

- **Status:** Draft — awaiting user approval
- **Phase:** Requirements Discovery
- **Next phase after approval:** Architecture Design

