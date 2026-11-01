Started at: 2026/10/12 15:18:00
Finished at: 2026/10/12 15:18:00
Total time: 0 minutes
---

# Contracts: Identity & Access

## Source

- **Vision:** [2026-10-12-vision.md](./2026-10-12-vision.md)
- **Requirements:** [2026-10-12-requirements.md](./2026-10-12-requirements.md)
- **Architecture:** [2026-10-12-architecture.md](./2026-10-12-architecture.md)
- **Parent contracts:** [POS Platform Contracts](../pos-platform/2026-10-12-contracts.md)

## Contract Principles

- Public synchronous APIs use REST + OpenAPI 3.1 under `/api/v1`.
- Internal service-to-service APIs use REST + OpenAPI 3.1 under `/internal/v1` and require workload identity/mTLS.
- Events use the parent envelope and JSON Schema/AsyncAPI contracts.
- Identity DB is the source of truth for tenant, branch, membership, role and authorization state.
- Cognito `sub` is an opaque external identifier; it is never accepted from a public request body as an authority claim.
- `tenantId` is selected through an explicit tenant resource path for Identity APIs. No `X-Active-Tenant-Id` header is introduced. `X-Active-Branch-Id` may select branch context only after server validation.
- All timestamps are ISO-8601 UTC strings in APIs and `TIMESTAMPTZ` in PostgreSQL.
- All service-owned IDs are UUID v7 strings.

## Common HTTP Contract

### Headers

| Header | Required | Meaning |
|---|---:|---|
| `Authorization` | Public protected APIs | `Bearer <Cognito access token>`; Cognito login itself happens at the Cognito hosted UI/SDK, not in this service. |
| `X-Correlation-Id` | Recommended; generated if absent | Correlates request, audit, outbox and downstream calls. |
| `Idempotency-Key` | Required for retryable mutations | Client-generated key, scoped to actor + tenant; same key must carry the same request hash. |
| `If-Match` | Required for updates to versioned resources | ETag/version returned by the previous read, e.g. `"membership-v7"`. |
| `X-Active-Branch-Id` | Branch-scoped public operation | Client-selected branch; server revalidates against current membership. Never trusted alone. |
| `X-Pos-Operator-Context` | P1 after quick switch | Opaque short-lived context returned by the PIN endpoint; downstream services send it to Identity for validation. |
| `Content-Type` | Requests with body | `application/json; charset=utf-8`. |

Internal calls additionally require workload identity/mTLS. Public clients cannot set or route to `/internal/v1`.

### Success Envelope

```json
{
  "data": {},
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

Collection responses use the same envelope with `meta.pagination`:

```json
{
  "data": [],
  "meta": {
    "pagination": {
      "page": 1,
      "limit": 20,
      "total": 42,
      "totalPages": 3
    },
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

### Error Envelope

```json
{
  "error": {
    "code": "forbidden",
    "message": "The caller is not allowed to access this resource",
    "details": [],
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

Error rules:

| HTTP | Code | Identity use |
|---:|---|---|
| 401 | `unauthorized` | Missing, malformed, expired or invalid Cognito JWT. |
| 403 | `forbidden` | Authenticated but outside role/tenant/branch/device scope. |
| 404 | `not_found` | Resource does not exist within the caller’s authorized scope. Do not reveal cross-tenant existence. |
| 409 | `conflict` | State, uniqueness, optimistic version or idempotency conflict. |
| 409 | `duplicate_request` | Same idempotency key used with a different request hash. |
| 409 | `last_owner_protected` | Mutation would remove the last active owner. |
| 409 | `membership_state_conflict` | Invalid lifecycle transition. |
| 409 | `invitation_pending` | A new invitation is already active for the same target scope. |
| 422 | `validation_error` | Invalid email, role, branch, PIN, ID or field constraint. |
| 429 | `rate_limited` | Invitation, activation, device or PIN limit exceeded. Includes `Retry-After`. |
| 503 | `cognito_unavailable` | Cognito/email dependency unavailable; durable invitation remains retryable. |
| 503 | `authorization_unavailable` | Current authorization cannot be resolved; protected operation fails closed. |
| 500 | `internal_error` | Unexpected failure without stack trace or sensitive detail. |

## Public API Contracts

### Identity and Current Context

#### `GET /api/v1/me`

Returns the authenticated user and all currently authorized memberships. No client-supplied role, tenant or branch is accepted.

- **Auth:** Cognito JWT.
- **Headers:** `X-Correlation-Id`.
- **Success:** `200 OK`.
- **Errors:** `401`.

```json
{
  "data": {
    "user": {
      "id": "0192b2d0-7f1a-7abc-8d2c-123456789012",
      "email": "manager@example.com",
      "displayName": "Nguyen Van A",
      "status": "ACTIVE"
    },
    "memberships": [
      {
        "id": "0192b2d0-7f1b-7abc-8d2c-123456789012",
        "tenantId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
        "tenantName": "Coffee Chain",
        "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
        "branchName": "District 1",
        "role": "MANAGER",
        "status": "ACTIVE",
        "permissions": [
          "branch:read",
          "employee:invite",
          "employee:manage"
        ],
        "version": 7
      }
    ]
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

`cognitoSubject`, access tokens, refresh tokens and raw PIN data are not returned.

### Tenant Bootstrap

#### `POST /api/v1/tenants`

Creates a tenant and first owner invitation. This is a protected platform bootstrap operation, not a capability granted to a normal tenant `OWNER`.

- **Auth:** platform-admin/workload bootstrap identity.
- **Headers:** `X-Correlation-Id`, `Idempotency-Key`.
- **Request:**

```json
{
  "name": "Coffee Chain",
  "owner": {
    "email": "owner@example.com",
    "displayName": "Owner Name"
  }
}
```

- **Response:** `201 Created`, `TenantBootstrapResponse`.
- **Errors:** `409`, `422`, `503`.

### Branches

#### `GET /api/v1/tenants/{tenantId}/branches`

- **Auth:** owner for tenant-wide list; manager for assigned branch list; other roles only if the branch is needed by their current authorized context.
- **Query:** `page` default 1, `limit` default 20 max 100, `status`, `search`, `sort` allowlist.
- **Response:** `200`, `BranchPage`.
- **Errors:** `401`, `403`, `404`, `422`.

#### `POST /api/v1/tenants/{tenantId}/branches`

- **Auth:** `OWNER` with tenant scope.
- **Headers:** `Idempotency-Key`, `X-Correlation-Id`.
- **Request:**

```json
{
  "code": "D1",
  "name": "District 1",
  "address": "1 Nguyen Hue, HCMC"
}
```

- **Response:** `201`, `BranchResponse`.
- **Errors:** `403`, `409`, `422`.

#### `PATCH /api/v1/tenants/{tenantId}/branches/{branchId}`

- **Auth:** `OWNER` with tenant scope.
- **Headers:** `If-Match`, `X-Correlation-Id`, `Idempotency-Key`.
- **Request:** partial `name`, `address`; `code` is immutable after creation.
- **Response:** `200`, `BranchResponse`.
- **Errors:** `403`, `404`, `409`, `422`.

#### `POST /api/v1/tenants/{tenantId}/branches/{branchId}/archive`

- **Auth:** `OWNER` with tenant scope.
- **Headers:** `If-Match`, `Idempotency-Key`, `X-Correlation-Id`.
- **Request:** `{ "reason": "Branch closed" }`, reason max 500 characters.
- **Response:** `200`, archived `BranchResponse`.
- **Errors:** `403`, `404`, `409`, `422`.

### Employees and Memberships

#### `GET /api/v1/tenants/{tenantId}/employees`

Lists employee profiles and their visible memberships.

- **Auth:** `OWNER` tenant-wide; `MANAGER` assigned branches only.
- **Query:** `page`, `limit`, `branchId`, `role`, `status`, `search`, `sort` allowlist.
- **Response:** `200`, `EmployeePage` using offset pagination.
- **Errors:** `401`, `403`, `404`, `422`.

#### `GET /api/v1/tenants/{tenantId}/employees/{userId}`

- **Auth:** owner or manager within visible employee scope.
- **Response:** `200`, `EmployeeResponse`.
- **Errors:** `401`, `403`, `404`.

#### `PATCH /api/v1/tenants/{tenantId}/employees/{userId}`

Updates non-authentication profile fields.

- **Auth:** owner; manager only for employees in assigned branches.
- **Headers:** `If-Match`, `Idempotency-Key`, `X-Correlation-Id`.
- **Request:** `{ "displayName": "New Name", "phone": "+84901234567" }`.
- **Response:** `200`, `EmployeeResponse`.
- **Errors:** `403`, `404`, `409`, `422`.

#### `GET /api/v1/tenants/{tenantId}/memberships`

Lists memberships directly for admin grids and audit workflows.

- **Auth:** owner tenant-wide; manager assigned branches only.
- **Query:** `page`, `limit`, `branchId`, `role`, `status`, `email`, `sort` allowlist.
- **Response:** `200`, `MembershipPage`.
- **Errors:** `401`, `403`, `404`, `422`.

#### `POST /api/v1/tenants/{tenantId}/memberships`

Creates an invitation and membership.

- **Auth:** owner for any allowed role/scope; manager only for `CASHIER`/`KITCHEN` in an assigned branch.
- **Headers:** `Idempotency-Key`, `X-Correlation-Id`.
- **Request:**

```json
{
  "email": "cashier@example.com",
  "displayName": "Cashier Name",
  "phone": "+84901234567",
  "role": "CASHIER",
  "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789012"
}
```

Rules:

- `OWNER` has `branchId: null`.
- `MANAGER`, `CASHIER` and `KITCHEN` require an active `branchId`.
- Manager requests for `OWNER`/`MANAGER`, archived branches or another manager’s scope return `403`.
- Email is normalized before uniqueness/idempotency checks.

- **Response:** `201`, `MembershipResponse` with `status: INVITED` and `invitationStatus: QUEUED`.
- **Errors:** `403`, `409`, `422`, `429`, `503`.

#### `GET /api/v1/tenants/{tenantId}/memberships/{membershipId}`

- **Auth:** owner or manager within the membership’s visible branch scope.
- **Response:** `200`, `MembershipResponse`.
- **Errors:** `403`, `404`.

#### `PATCH /api/v1/tenants/{tenantId}/memberships/{membershipId}`

Changes role or branch assignment. Status transitions use dedicated endpoints.

- **Auth:** owner for all allowed role/scope changes; manager only for `CASHIER`/`KITCHEN` within assigned branch and cannot move scope outside the manager’s branches.
- **Headers:** `If-Match`, `Idempotency-Key`, `X-Correlation-Id`.
- **Request:**

```json
{
  "role": "CASHIER",
  "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789012"
}
```

- **Response:** `200`, new `MembershipResponse` with incremented `version`.
- **Errors:** `403`, `404`, `409`, `422`.

#### `POST /api/v1/tenants/{tenantId}/memberships/{membershipId}/suspend`

- **Auth:** owner; manager for cashier/kitchen membership in assigned branch.
- **Headers:** `If-Match`, `Idempotency-Key`, `X-Correlation-Id`.
- **Request:** `{ "reason": "Leave of absence" }`.
- **Response:** `200`, `MembershipResponse` with `status: SUSPENDED`.
- **Errors:** `403`, `404`, `409`, `422`.

#### `POST /api/v1/tenants/{tenantId}/memberships/{membershipId}/reactivate`

- **Auth:** same scope as suspend.
- **Headers:** `If-Match`, `Idempotency-Key`, `X-Correlation-Id`.
- **Response:** `200`, `MembershipResponse` with `status: ACTIVE`.
- **Errors:** `403`, `404`, `409`, `422`.

#### `POST /api/v1/tenants/{tenantId}/memberships/{membershipId}/revoke`

Terminally revokes one membership history record.

- **Auth:** owner; manager for cashier/kitchen membership in assigned branch.
- **Headers:** `If-Match`, `Idempotency-Key`, `X-Correlation-Id`.
- **Request:** `{ "reason": "Employment ended" }`.
- **Response:** `200`, `MembershipResponse` with `status: REVOKED`.
- **Errors:** `403`, `404`, `409`, `422`.

#### `POST /api/v1/tenants/{tenantId}/memberships/{membershipId}/invitation-resends`

Queues a new Cognito invitation delivery attempt for an `INVITED` membership.

- **Auth:** owner; manager for cashier/kitchen membership in assigned branch.
- **Headers:** `Idempotency-Key`, `X-Correlation-Id`.
- **Response:** `202 Accepted`, `InvitationDeliveryResponse`.
- **Errors:** `403`, `404`, `409`, `429`, `503`.

### P1 POS Device and PIN APIs

These endpoints are versioned and feature-flagged. Their absence must not affect P0 login, membership or authorization APIs.

#### `GET /api/v1/tenants/{tenantId}/branches/{branchId}/pos-devices`

- **Auth:** owner or manager assigned to the branch.
- **Response:** `200`, `PosDevicePage`.

#### `POST /api/v1/tenants/{tenantId}/branches/{branchId}/pos-devices`

Registers a device using a client-generated device proof. The service stores only a hash/verification representation.

- **Auth:** owner or manager assigned to the branch.
- **Headers:** `Idempotency-Key`, `X-Correlation-Id`.
- **Request:**

```json
{
  "name": "Counter POS 01",
  "deviceProof": "client-generated-secret-or-key-proof"
}
```

- **Response:** `201`, `PosDeviceResponse`; the proof is never echoed.
- **Errors:** `403`, `409`, `422`, `429`.

#### `POST /api/v1/pos-devices/{deviceId}/revoke`

- **Auth:** owner or manager assigned to the device branch.
- **Headers:** `Idempotency-Key`, `X-Correlation-Id`.
- **Request:** `{ "reason": "Device replaced" }`.
- **Response:** `200`, revoked `PosDeviceResponse`.
- **Errors:** `403`, `404`, `409`.

#### `PUT /api/v1/pos-devices/{deviceId}/cashier-pins/me`

Sets or rotates the current authenticated cashier’s device-scoped PIN.

- **Auth:** authenticated `CASHIER` membership for the device branch, or an owner who also has an active cashier membership.
- **Headers:** `Idempotency-Key`, `X-Correlation-Id`.
- **Request:**

```json
{
  "pin": "482915",
  "pinConfirmation": "482915"
}
```

- **Response:** `204 No Content`.
- **Errors:** `403`, `404`, `409`, `422`, `429`.

#### `POST /api/v1/pos-devices/{deviceId}/quick-switch`

Switches the effective operator to a cashier inside an already authenticated POS session.

- **Auth:** valid Cognito JWT; base session must have access to the device branch.
- **Headers:** `X-Active-Branch-Id`, `X-Correlation-Id`, `Idempotency-Key`.
- **Request:**

```json
{
  "cashierMembershipId": "0192b2d0-7f1b-7abc-8d2c-123456789012",
  "pin": "482915",
  "deviceProof": "client-generated-secret-or-key-proof"
}
```

- **Response:** `200`, `PosOperatorContextResponse` with an opaque short-lived context.
- **Errors:** `403`, `404`, `409`, `422`, `429`.
- Raw PIN is never persisted in an idempotency response or logged. If a request is retried, the server may return a new context while preserving the mutation/audit semantics.

#### `POST /api/v1/pos-devices/{deviceId}/quick-switch-sessions/{sessionId}/revoke`

- **Auth:** base authenticated POS session or owner/manager in device branch.
- **Headers:** `Idempotency-Key`, `X-Correlation-Id`.
- **Response:** `204 No Content`.
- **Errors:** `403`, `404`, `409`.

## Internal API Contracts

### `POST /internal/v1/authorization-decisions`

Resolves current authorization for a downstream service. The caller is a trusted workload that has independently verified the user JWT. Identity Access verifies the workload identity and current membership state.

- **Auth:** mTLS/workload credential; never public.
- **Request:**

```json
{
  "subject": "cognito-sub-opaque",
  "tenantId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
  "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "requiredPermission": "order:create",
  "posOperatorContext": "opaque-context-if-present"
}
```

- **Response `200`:**

```json
{
  "data": {
    "allowed": true,
    "principal": {
      "userId": "0192b2d0-7f1e-7abc-8d2c-123456789012",
      "membershipId": "0192b2d0-7f1b-7abc-8d2c-123456789012",
      "tenantId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
      "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
      "role": "CASHIER",
      "permissions": ["order:read", "order:create"],
      "membershipVersion": 7,
      "effectivePrincipal": "0192b2d0-7f1e-7abc-8d2c-123456789012"
    }
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Denied decision:** return `200` with `allowed: false` for a valid decision request, plus a non-sensitive `denialCode`; the downstream service maps it to `403`.
- **Errors:** `401`/`403` for workload identity, `422` for malformed request, `503` when current authorization cannot be safely resolved.
- The `subject` is trusted only because the workload request is authenticated; a public client cannot call this endpoint.

### `POST /internal/v1/authorization-contexts/invalidate`

Optional operational endpoint for cache invalidation after an authorization-change event. It is not used as the P0 authorization source of truth.

- **Auth:** Event consumer/workload identity.
- **Request:** `{ "tenantId": "...", "userId": "...", "membershipVersion": 8, "eventId": "..." }`.
- **Response:** `202 Accepted`.
- **Errors:** `401`, `403`, `422`.

### `POST /internal/v1/invitation-deliveries/{deliveryId}/retry`

Queues a bounded Cognito invitation retry or reconciliation attempt. Only the worker/operator role may call it.

- **Auth:** worker identity.
- **Headers:** `Idempotency-Key`, `X-Correlation-Id`.
- **Response:** `202 Accepted`, `InvitationDeliveryResponse`.
- **Errors:** `401`, `403`, `404`, `409`, `429`, `503`.

## Reusable Schemas

### `TenantResponse`

| Field | Type | Required | Meaning |
|---|---|---:|---|
| `id` | UUID string | yes | Tenant ID. |
| `name` | string | yes | Display name, 1–200 Unicode characters. |
| `status` | enum | yes | `ACTIVE`, `SUSPENDED`, `ARCHIVED`. |
| `createdAt` / `updatedAt` | ISO-8601 UTC | yes | Audit timestamps. |
| `version` | integer | yes | Optimistic concurrency version. |

### `BranchResponse`

| Field | Type | Required | Meaning |
|---|---|---:|---|
| `id` | UUID string | yes | Branch ID. |
| `tenantId` | UUID string | yes | Server-owned tenant relationship. |
| `code` | string | yes | Immutable tenant-scoped branch code. |
| `name` | string | yes | Branch display name. |
| `address` | string/null | no | Optional display address; max 500 characters. |
| `status` | enum | yes | `ACTIVE`, `ARCHIVED`. |
| `version` | integer | yes | Optimistic concurrency version. |
| `createdAt` / `updatedAt` | ISO-8601 UTC | yes | Audit timestamps. |

### `EmployeeResponse`

| Field | Type | Required | Meaning |
|---|---|---:|---|
| `userId` | UUID string | yes | Internal user ID. |
| `email` | string | yes for authorized admin views | Normalized email; masked when policy requires. |
| `displayName` | string | yes | Employee display name. |
| `phone` | string/null | no | Optional phone; masked outside management scope. |
| `status` | enum | yes | `ACTIVE`, `SUSPENDED`, `REVOKED`, `INVITED` derived from visible memberships. |
| `memberships` | array | yes | Visible membership summaries. |
| `createdAt` / `updatedAt` | ISO-8601 UTC | yes | Profile timestamps. |

### `MembershipResponse`

| Field | Type | Required | Meaning |
|---|---|---:|---|
| `id` | UUID string | yes | Membership ID/history record. |
| `tenantId` | UUID string | yes | Tenant scope. |
| `branchId` | UUID/null | conditional | Null only for tenant-wide owner membership. |
| `userId` | UUID string | yes | Internal user ID. |
| `email` | string | yes | Normalized/masked employee email. |
| `role` | enum | yes | `OWNER`, `MANAGER`, `CASHIER`, `KITCHEN`. |
| `status` | enum | yes | `INVITED`, `ACTIVE`, `SUSPENDED`, `REVOKED`. |
| `invitationStatus` | enum/null | no | `QUEUED`, `SENT`, `FAILED`, `ACCEPTED`, `NOT_APPLICABLE`. |
| `permissions` | array | yes | Server-derived permissions for this membership. |
| `version` | integer | yes | Optimistic concurrency version. |
| `createdAt` / `updatedAt` | ISO-8601 UTC | yes | Lifecycle timestamps. |

### `InvitationDeliveryResponse`

| Field | Type | Required | Meaning |
|---|---|---:|---|
| `deliveryId` | UUID string | yes | Delivery attempt ID. |
| `membershipId` | UUID string | yes | Target membership. |
| `status` | enum | yes | `QUEUED`, `SENT`, `FAILED`, `ACCEPTED`. |
| `attemptCount` | integer | yes | Number of provider attempts; never includes provider secrets. |
| `nextAttemptAt` | ISO-8601 UTC/null | no | Retry time when queued/failed. |
| `lastErrorCode` | string/null | no | Safe machine-readable failure code. |

### `PosOperatorContextResponse` (P1)

| Field | Type | Required | Meaning |
|---|---|---:|---|
| `sessionId` | UUID string | yes | Server-side switch session ID. |
| `contextToken` | string | yes | Opaque short-lived token; client treats it as secret. |
| `expiresAt` | ISO-8601 UTC | yes | Hard expiration. |
| `tenantId` / `branchId` | UUID string | yes | Device-bound scope. |
| `effectiveMembershipId` | UUID string | yes | Active cashier membership. |
| `role` | enum | yes | Always `CASHIER` for quick switch. |
| `permissions` | array | yes | Narrowed cashier permissions only. |

## Data Contracts

### PostgreSQL Conventions

- Database: PostgreSQL/Aurora PostgreSQL.
- IDs: `uuid` containing application-generated UUID v7.
- Strings: `text`; avoid arbitrary `varchar(n)` limits unless a business validation boundary requires one.
- Time: `timestamptz`.
- Structured payloads: `jsonb` only for bounded provider metadata, redacted event payloads and stored idempotent response metadata.
- Every column in a migration must include a descriptive `COMMENT ON COLUMN` or equivalent Liquibase remark.
- Table/constraint/index names use plural tables, `fk_`, `uq_`, `chk_` and `idx_` prefixes.

### `tenants`

| Column | Type | Null | Constraints | Column remark |
|---|---|---:|---|---|
| `id` | uuid | no | PK | Stable tenant identifier generated by Identity Access. |
| `name` | text | no | `CHECK length(name) BETWEEN 1 AND 200` | Tenant display name. |
| `status` | text | no | `ACTIVE/SUSPENDED/ARCHIVED` | Tenant lifecycle state. |
| `version` | bigint | no | default 1, positive | Optimistic concurrency version. |
| `created_at` | timestamptz | no | default now | Creation timestamp in UTC. |
| `updated_at` | timestamptz | no | default now | Last mutation timestamp in UTC. |
| `created_by` | text | no | opaque actor reference | Actor that created the tenant/bootstrap record. |
| `updated_by` | text | no | opaque actor reference | Actor that last changed the tenant. |

### `branches`

| Column | Type | Null | Constraints | Column remark |
|---|---|---:|---|---|
| `id` | uuid | no | PK | Stable branch identifier. |
| `tenant_id` | uuid | no | FK `tenants(id)` | Tenant that owns the branch. |
| `code` | text | no | immutable | Human-facing branch code as entered by owner. |
| `normalized_code` | text | no | unique per tenant | Case/space-normalized branch code used for uniqueness. |
| `name` | text | no | length 1–200 | Branch display name. |
| `address` | text | yes | max 500 by validation | Optional branch display address. |
| `status` | text | no | `ACTIVE/ARCHIVED` | Branch lifecycle state. |
| `version` | bigint | no | default 1, positive | Optimistic concurrency version. |
| `created_at` | timestamptz | no | default now | Creation timestamp. |
| `updated_at` | timestamptz | no | default now | Last mutation timestamp. |
| `created_by` | text | no | opaque actor reference | Creator actor reference. |
| `updated_by` | text | no | opaque actor reference | Last updater actor reference. |

### `users`

| Column | Type | Null | Constraints | Column remark |
|---|---|---:|---|---|
| `id` | uuid | no | PK | Internal user identifier. |
| `cognito_subject` | text | yes | unique when not null | Stable Cognito `sub`; null until invitation activation/reconciliation. |
| `normalized_email` | text | no | unique | Lowercase/trimmed invitation and identity lookup email. |
| `display_name` | text | no | length 1–200 | Minimal employee display name. |
| `phone` | text | yes | validated/normalized | Optional employee phone; protected PII. |
| `status` | text | no | `ACTIVE/SUSPENDED/REVOKED` | User-level profile status; membership status remains authoritative per scope. |
| `reconciliation_status` | text | no | controlled enum | Cognito mapping state, e.g. `PENDING`, `ALIGNED`, `DRIFTED`. |
| `last_authenticated_at` | timestamptz | yes | — | Last observed successful authenticated request. |
| `created_at` | timestamptz | no | default now | Local profile creation timestamp. |
| `updated_at` | timestamptz | no | default now | Last profile/mapping mutation timestamp. |

### `memberships`

| Column | Type | Null | Constraints | Column remark |
|---|---|---:|---|---|
| `id` | uuid | no | PK | Membership history record identifier. |
| `tenant_id` | uuid | no | FK `tenants(id)` | Tenant scope of the membership. |
| `user_id` | uuid | no | FK `users(id)` | User receiving the membership. |
| `branch_id` | uuid | yes | composite tenant/branch ownership check | Assigned branch; null only for tenant-wide owner membership. |
| `role` | text | no | `OWNER/MANAGER/CASHIER/KITCHEN` | One effective role for this membership scope. |
| `status` | text | no | `INVITED/ACTIVE/SUSPENDED/REVOKED` | Membership lifecycle state. |
| `status_reason` | text | yes | max 500 | Safe operator-provided reason for suspension/revocation. |
| `version` | bigint | no | default 1, positive | Version used for `If-Match` optimistic concurrency. |
| `invited_at` | timestamptz | no | default now | Time the membership invitation was created. |
| `activated_at` | timestamptz | yes | — | First time Cognito subject and membership became active. |
| `suspended_at` | timestamptz | yes | — | Most recent suspension timestamp. |
| `revoked_at` | timestamptz | yes | — | Terminal revocation timestamp. |
| `created_at` | timestamptz | no | default now | Membership history creation timestamp. |
| `updated_at` | timestamptz | no | default now | Last membership mutation timestamp. |
| `created_by` | text | no | opaque actor reference | Actor that created the membership. |
| `updated_by` | text | no | opaque actor reference | Actor that last changed the membership. |

Required constraints:

- `OWNER` requires `branch_id IS NULL`.
- `MANAGER`, `CASHIER`, `KITCHEN` require `branch_id IS NOT NULL`.
- Active/invited tenant-wide membership is unique by `(tenant_id, user_id)` when `branch_id IS NULL`.
- Active/invited branch membership is unique by `(tenant_id, user_id, branch_id)` when `branch_id IS NOT NULL`.
- A branch belongs to the same `tenant_id` as the membership; enforce with a composite key/FK or equivalent transaction-level constraint.
- `REVOKED` is terminal; reinvitation creates a new row.

### `invitation_deliveries`

| Column | Type | Null | Constraints | Column remark |
|---|---|---:|---|---|
| `id` | uuid | no | PK | Invitation delivery attempt identifier. |
| `membership_id` | uuid | no | FK `memberships(id)` | Membership targeted by the delivery. |
| `provider` | text | no | `COGNITO` | External invitation provider. |
| `status` | text | no | `QUEUED/SENT/FAILED/ACCEPTED` | Delivery/reconciliation state. |
| `attempt_count` | integer | no | non-negative | Number of provider calls made. |
| `next_attempt_at` | timestamptz | yes | — | Next bounded retry time. |
| `last_error_code` | text | yes | redacted machine code | Safe failure code; no raw provider payload. |
| `provider_user_reference` | text | yes | opaque | Provider-side reference if available; never a secret. |
| `created_at` | timestamptz | no | default now | Delivery record creation timestamp. |
| `updated_at` | timestamptz | no | default now | Last delivery state timestamp. |

### `idempotency_records`

| Column | Type | Null | Constraints | Column remark |
|---|---|---:|---|---|
| `id` | uuid | no | PK | Idempotency record identifier. |
| `tenant_id` | uuid | yes | FK when tenant-scoped | Tenant scope; null only for platform bootstrap. |
| `actor_subject` | text | no | opaque | Authenticated/workload actor scope for the key. |
| `idempotency_key` | text | no | max 200 | Client retry key. |
| `request_hash` | text | no | cryptographic digest | Canonical request payload digest used to detect key reuse with a different payload. |
| `response_status` | integer | no | HTTP status | Original committed response status. |
| `response_metadata` | jsonb | no | redacted/bounded | Replay-safe response metadata without raw PIN/token/secret. |
| `expires_at` | timestamptz | no | retention policy | Time after which the retry record may be cleaned up. |
| `created_at` | timestamptz | no | default now | Record creation timestamp. |

### `outbox_events`

| Column | Type | Null | Constraints | Column remark |
|---|---|---:|---|---|
| `id` | uuid | no | PK | Stable event ID used for consumer deduplication. |
| `tenant_id` | uuid | yes | FK when tenant-scoped | Tenant carried by the event; null only for platform events. |
| `branch_id` | uuid | yes | validated against tenant | Branch carried by a branch-scoped event. |
| `event_type` | text | no | allowlisted | Versioned event type name. |
| `event_version` | integer | no | positive | Event schema version. |
| `aggregate_type` | text | no | allowlisted | Aggregate family that produced the event. |
| `aggregate_id` | uuid | no | — | Aggregate identifier. |
| `payload` | jsonb | no | redacted schema | Event payload excluding secrets and raw PII. |
| `correlation_id` | text | no | — | Request/workflow correlation identifier. |
| `causation_id` | text | yes | — | Parent event/command identifier. |
| `publish_status` | text | no | `PENDING/PUBLISHED/FAILED` | Publisher lifecycle state. |
| `attempt_count` | integer | no | non-negative | Number of broker publish attempts. |
| `next_attempt_at` | timestamptz | yes | — | Next retry time. |
| `published_at` | timestamptz | yes | — | Broker acknowledgement timestamp. |
| `created_at` | timestamptz | no | default now | Event creation timestamp. |

### `pos_devices` (P1)

| Column | Type | Null | Constraints | Column remark |
|---|---|---:|---|---|
| `id` | uuid | no | PK | Registered POS device identifier. |
| `tenant_id` | uuid | no | FK `tenants(id)` | Tenant scope of the device. |
| `branch_id` | uuid | no | FK/tenant consistency | Branch to which the device is bound. |
| `name` | text | no | length 1–200 | Operator-facing device name. |
| `device_key_hash` | text | no | unique | Hash/verification representation of client device proof. |
| `status` | text | no | `ACTIVE/REVOKED` | Device lifecycle state. |
| `registered_by_user_id` | uuid | no | FK `users(id)` | Authenticated user that registered the device. |
| `last_seen_at` | timestamptz | yes | — | Last verified device activity timestamp. |
| `revoked_at` | timestamptz | yes | — | Device revocation timestamp. |
| `created_at` | timestamptz | no | default now | Device registration timestamp. |
| `updated_at` | timestamptz | no | default now | Last device state timestamp. |

### `pos_pins` (P1)

| Column | Type | Null | Constraints | Column remark |
|---|---|---:|---|---|
| `id` | uuid | no | PK | Device-scoped PIN credential identifier. |
| `membership_id` | uuid | no | FK `memberships(id)` | Cashier membership owning the PIN. |
| `device_id` | uuid | no | FK `pos_devices(id)` | POS device on which the PIN is valid. |
| `pin_hash` | text | no | Argon2id/bcrypt output | One-way hash of the six-digit PIN. |
| `status` | text | no | `ACTIVE/REVOKED` | PIN credential lifecycle. |
| `failed_attempts` | integer | no | non-negative | Consecutive failed attempts since last successful verification. |
| `locked_until` | timestamptz | yes | — | Temporary lockout end time. |
| `rotated_at` | timestamptz | no | — | Last PIN set/rotation timestamp. |
| `created_at` | timestamptz | no | default now | PIN credential creation timestamp. |
| `updated_at` | timestamptz | no | default now | Last PIN credential mutation timestamp. |

### `pos_switch_sessions` (P1)

| Column | Type | Null | Constraints | Column remark |
|---|---|---:|---|---|
| `id` | uuid | no | PK | Quick-switch session identifier. |
| `tenant_id` | uuid | no | FK `tenants(id)` | Tenant scope of the switch context. |
| `branch_id` | uuid | no | FK/tenant consistency | Branch scope of the switch context. |
| `device_id` | uuid | no | FK `pos_devices(id)` | Device binding. |
| `base_user_id` | uuid | no | FK `users(id)` | Cognito-authenticated base user. |
| `effective_membership_id` | uuid | no | active cashier FK | Cashier membership selected by PIN. |
| `membership_version` | bigint | no | positive | Membership version checked when context was created. |
| `context_token_hash` | text | no | unique | Hash of opaque context token; raw token is returned once and not persisted. |
| `expires_at` | timestamptz | no | short TTL | Context hard expiration. |
| `revoked_at` | timestamptz | yes | — | Explicit or cascading revocation timestamp. |
| `created_at` | timestamptz | no | default now | Context creation timestamp. |

## Indexes

| Table | Index/constraint | Columns | Type | Rationale |
|---|---|---|---|---|
| `branches` | `uq_branches_tenant_code` | `(tenant_id, normalized_code)` | unique B-tree | Tenant-scoped branch code uniqueness. |
| `branches` | `idx_branches_tenant_status` | `(tenant_id, status, id)` | B-tree | Branch list and authorized filtering. |
| `users` | `uq_users_cognito_subject` | `(cognito_subject)` where non-null | unique partial B-tree | Stable Cognito mapping. |
| `users` | `uq_users_normalized_email` | `(normalized_email)` | unique B-tree | Cognito invitation identity lookup. |
| `memberships` | `uq_memberships_active_tenant_user` | `(tenant_id, user_id)` where branch null and status in invited/active/suspended | unique partial B-tree | At most one tenant-wide active/invited membership per user. |
| `memberships` | `uq_memberships_active_branch_user` | `(tenant_id, user_id, branch_id)` where branch not null and status in invited/active/suspended | unique partial B-tree | At most one effective membership per user/branch. |
| `memberships` | `idx_memberships_subject_status` | `(user_id, status, tenant_id, branch_id)` | B-tree | `/me` and authorization lookup. |
| `memberships` | `idx_memberships_scope_status` | `(tenant_id, branch_id, status, role, user_id)` | B-tree | Owner/manager employee lists and scope checks. |
| `invitation_deliveries` | `idx_invitation_retry` | `(status, next_attempt_at)` | B-tree | Worker retry polling. |
| `idempotency_records` | `uq_idempotency_actor_key` | `(tenant_id, actor_subject, idempotency_key)` | unique B-tree | Exactly-once request replay semantics within retention. |
| `outbox_events` | `idx_outbox_publish_queue` | `(publish_status, next_attempt_at, created_at)` | B-tree | Bounded publisher polling. |
| `outbox_events` | `idx_outbox_aggregate` | `(aggregate_type, aggregate_id, created_at)` | B-tree | Operational trace/replay by aggregate. |
| `pos_devices` | `uq_pos_devices_key_hash` | `(device_key_hash)` | unique B-tree | Prevent duplicate registered device proof. |
| `pos_devices` | `idx_pos_devices_scope` | `(tenant_id, branch_id, status)` | B-tree | Branch device listing/revocation. |
| `pos_pins` | `uq_pos_pins_device_membership` | `(device_id, membership_id)` where status active | unique partial B-tree | One active device PIN per cashier membership. |
| `pos_switch_sessions` | `uq_pos_switch_context_hash` | `(context_token_hash)` | unique B-tree | Opaque context lookup without storing raw token. |
| `pos_switch_sessions` | `idx_pos_switch_expiry` | `(expires_at, revoked_at)` | B-tree | Cleanup and validity checks. |

## Migration Plan

Flyway versioned SQL is the selected migration format for the Spring Boot service.

| Migration | Contents | Rollback/repair note |
|---|---|---|
| `V1__create_identity_extensions.sql` | Schema, UUID/utility prerequisites and migration metadata. | Forward-only; remove only in disposable local database. |
| `V2__create_tenants_branches_users.sql` | `tenants`, `branches`, `users`, FKs, checks, comments and base indexes. | Restore from backup or use compensating migration; no destructive drop. |
| `V3__create_memberships.sql` | Membership lifecycle, role/branch checks, partial uniqueness and comments. | Backfill/validate before enabling constraints in existing data. |
| `V4__create_invitation_deliveries.sql` | Durable Cognito delivery/reconciliation records and retry indexes. | Preserve failed history; repair status through operator command. |
| `V5__create_idempotency_outbox.sql` | Idempotency and transactional outbox tables, indexes and comments. | Keep events for replay; do not delete unpublished records. |
| `V6__create_p1_pos_device_pin.sql` | Feature-flagged device/PIN/session tables and constraints. | Deploy schema before enabling feature flag; disable feature before rollback. |
| `V7__seed_local_demo_identity.sql` | Deterministic local tenant/branches/memberships; fake Cognito subject values only. | Local/demo only; never run against shared production data. |

Migration rules:

1. Every column in V2–V6 has a descriptive `COMMENT ON COLUMN`.
2. Additive/nullable changes deploy before application reads them; required constraints follow data backfill.
3. Validate migration against a copy/staging database before deployment.
4. No migration contains real credentials, Cognito secrets, plaintext PINs or production PII.

## Integration Event Contracts

### Event Envelope

```json
{
  "eventId": "0192b2d0-8000-7abc-8d2c-123456789012",
  "eventType": "MembershipRoleChanged",
  "eventVersion": 1,
  "occurredAt": "2026-10-12T08:30:00Z",
  "tenantId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
  "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "aggregateType": "membership",
  "aggregateId": "0192b2d0-7f1b-7abc-8d2c-123456789012",
  "correlationId": "cor_01J...",
  "causationId": "cmd_01J...",
  "producer": "identity-access",
  "payload": {}
}
```

### Event Registry

| Event | Producer | Consumers | Payload requirements |
|---|---|---|---|
| `TenantCreated.v1` | Identity Access | Audit, Reporting/admin projection | `tenantId`, name/status, actor reference, created timestamp. |
| `BranchCreated.v1` | Identity Access | Audit, downstream branch projections | `tenantId`, `branchId`, code/name/status, actor. |
| `BranchUpdated.v1` | Identity Access | Audit, downstream branch projections | Changed fields, version, actor, before/after redacted values. |
| `BranchArchived.v1` | Identity Access | Audit, downstream branch projections | Branch scope, reason, version, actor. |
| `MembershipInvited.v1` | Identity Access | Audit, notification/reconciliation worker | Membership scope/role, normalized target reference, delivery ID; no token. |
| `MembershipActivated.v1` | Identity Access | Audit, downstream authorization projections | Membership ID, user ID, tenant/branch/role, version. |
| `MembershipRoleChanged.v1` | Identity Access | Audit, optional authorization cache invalidators | Old/new role, scope, version, actor. |
| `MembershipScopeChanged.v1` | Identity Access | Audit, optional authorization cache invalidators | Old/new branch, role, version, actor. |
| `MembershipSuspended.v1` | Identity Access | Audit, optional authorization cache invalidators | Membership, reason, version, actor. |
| `MembershipRevoked.v1` | Identity Access | Audit, optional authorization cache invalidators | Membership, reason, version, actor. |
| `AuthorizationContextChanged.v1` | Identity Access | Cache invalidators, observability consumers | Subject/user, membership ID/version, reason; no raw token. |
| `PosDeviceRegistered.v1` | Identity Access | Audit | Device/tenant/branch/status, actor; no device proof. |
| `PosDeviceRevoked.v1` | Identity Access | Audit, POS/session invalidator | Device scope and reason; no device proof. |
| `PosPinChanged.v1` | Identity Access | Audit | Membership/device scope and outcome; never PIN/hash. |
| `PosQuickSwitchSucceeded.v1` | Identity Access | Audit/observability | Device, branch, base actor and effective cashier IDs, expiry; no PIN/context token. |
| `PosQuickSwitchDenied.v1` | Identity Access | Audit/observability | Device/branch, reason category, actor; no PIN. |

### Broker Routing and Delivery

| Item | Contract |
|---|---|
| Event bus | EventBridge bus `pos.identity.v1` in cloud; local adapter preserves detail-type and envelope. |
| Audit queue | `identity-to-audit.fifo` when ordering is required per aggregate, otherwise standard queue with event deduplication. |
| Projection/cache queue | `identity-authorization-invalidation` standard SQS queue. |
| Retry | Exponential backoff, bounded attempts, visibility timeout greater than max handler duration. |
| DLQ | One DLQ per consumer queue; message retains event ID, correlation ID and failure code. |
| Consumer idempotency | Consumer stores/derives processed event ID before acknowledging side effects. |
| Schema compatibility | Add optional fields only within a version; breaking payload changes create `eventVersion + 1`. |

## Permission Catalog

Permissions are server-defined strings, not client-created values.

| Permission | Default role(s) | Scope |
|---|---|---|
| `tenant:read` | `OWNER` | Tenant-wide |
| `branch:read` | `OWNER`, `MANAGER`, `CASHIER`, `KITCHEN` | Assigned/selected branch |
| `branch:manage` | `OWNER` | Tenant-wide |
| `employee:read` | `OWNER`, `MANAGER` | Tenant/assigned branch |
| `employee:invite` | `OWNER`, `MANAGER` | Tenant/assigned branch with role limits |
| `employee:manage` | `OWNER`, `MANAGER` | Tenant/assigned branch with role limits |
| `membership:assign` | `OWNER`, constrained `MANAGER` | Tenant/assigned branch with role limits |
| `membership:suspend` | `OWNER`, constrained `MANAGER` | Tenant/assigned branch |
| `membership:revoke` | `OWNER`, constrained `MANAGER` | Tenant/assigned branch |
| `pos-device:manage` | `OWNER`, constrained `MANAGER` | Assigned branch |
| `pos-pin:manage-self` | `CASHIER`, eligible `OWNER` | Device/branch |
| `pos:quick-switch` | Authenticated POS user | Registered device/branch; target always cashier |

Downstream business permissions such as `order:create`, `catalog:manage` or `kds:view` are defined by their owning service. Identity Access may carry them in a versioned role-permission mapping but does not own their business behavior.

## Contract Test Requirements

- OpenAPI validates every public/internal request, response and error envelope.
- JSON Schema/AsyncAPI validates event envelope, required metadata and version compatibility.
- Contract tests cover `401/403/404/409/422/429/503` identity-specific cases.
- Repository integration tests verify tenant/branch composite ownership, partial unique constraints, last-owner invariant and optimistic version conflict.
- Security contract tests attempt cross-tenant IDs, cross-branch headers, manager role escalation, revoked JWT reuse, malformed internal workload requests and raw secret/PIN leakage.
- Idempotency tests replay identical and conflicting keys for every retryable mutation.
- Cognito adapter tests cover invitation success, provider outage, duplicate user, accepted invitation, disabled user and reconciliation drift.
- P1 contract tests cover device binding, invalid PIN, lockout, revoked device, expired context and permission narrowing.

## Domain Skills Referenced

- **api-design:** REST resources, status codes, error envelope, authentication headers, pagination, rate limiting and versioning.
- **database-design:** PostgreSQL schema, UUID v7, column remarks, constraints, indexes, Flyway migration order, rollback/repair and partitioning decision.
- **log-processing:** redacted audit/event fields and prohibition on storing/logging JWTs, secrets and raw PINs.

## Approval

- **Status:** Draft — awaiting user approval
- **Phase:** API & Data Contracts
- **Next phase after approval:** Design Finalization

