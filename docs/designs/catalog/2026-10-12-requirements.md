Started at: 2026/10/12 16:18:00
Finished at: 2026/10/12 16:18:00
Total time: 0 minutes
---

# Requirements: Catalog Service

## Sources and Priority

- [Vision](./2026-10-12-vision.md) and [Spec Sentinel Report](./2026-10-12-vision-review.md).
- [POS Platform Requirements](../pos-platform/2026-10-12-requirements.md), [POS Platform Contracts](../pos-platform/2026-10-12-contracts.md), [Identity & Access Contracts](../identity-access/2026-10-12-contracts.md).
- **P0:** required for the first POS slice and automated acceptance. **P1:** designed as compatible extensions; not required for P0 release.
- A Catalog item is tenant-owned. In P0, the menu and its prices are shared across that tenant's branches. Stock and recipe state belong to Inventory.

## Actors and Authorization

| Actor | P0 read | P0 write | Scope |
|---|---|---|---|
| Owner | Yes | Yes | All branches of the selected tenant. |
| Manager | Yes | Yes | Must have an ACTIVE manager membership at an ACTIVE branch in the selected tenant. Writes change the shared menu for all tenant branches. |
| Cashier | Yes | No | POS menu only for an assigned, ACTIVE branch. |
| Kitchen | Yes | No | Read-only menu for an assigned, ACTIVE branch. |
| Order workload | Yes | No | Private current-item resolution for a validated tenant/branch request. |
| Recommendation/Reporting | Events/projection | No | Tenant-scoped events and projections. |

- Catalog must use the current Identity authorization decision. A Cognito JWT or client-provided role/branch alone never grants access.
- Every Catalog query and mutation filters by `tenant_id`; resource IDs in path, body and associations are checked against that tenant. Read restrictions also require an active branch membership for Manager/Cashier/Kitchen. An Owner can use tenant-wide context.
- Manager's P0 write authority is a deliberate tenant-wide Catalog capability granted by any active manager branch membership in that tenant; it does not grant branch administration, employee administration or catalog access in another tenant.
- Cashier is read-only. The parent `POST /api/v1/catalog/items/{itemId}/availability` cashier grant is superseded for this module and must be amended before implementation.
- Management mutations are audited with actor, selected membership/branch, tenant-wide effect, target, old/new summary and correlation ID.

## Functional Requirements

| ID | Priority | Actor | Action | Required response |
|---|---|---|---|---|
| CAT-UC-01 | P0 | Owner/Manager | Create, rename, reorder or archive a flat category | Category is tenant-owned; archive preserves history and prevents active items from becoming uncategorized. |
| CAT-UC-02 | P0 | Owner/Manager | Create item in one category with at least one priced variant | Atomically creates item/variant(s), validates tenant ownership, unique SKU and default variant. |
| CAT-UC-03 | P0 | Owner/Manager | Edit item name, SKU, category or display order | Optimistic version check; scoped update and audit/event record. |
| CAT-UC-04 | P0 | Owner/Manager | Set item `ACTIVE`/`INACTIVE` or archive | Applies cascade sellability; historical references remain resolvable by authorized internal callers. |
| CAT-UC-05 | P0 | Owner/Manager | Add, edit, reorder or disable variant/size | Price is nonnegative integer VND; active item retains an active default or activation fails. |
| CAT-UC-06 | P0 | Owner/Manager | Create/edit/disable reusable modifier group and options | Group and options are tenant-owned; selection and price rules validated. |
| CAT-UC-07 | P0 | Owner/Manager | Attach/detach modifier group to item with optional rule override | Effective min/max and ordering are saved for that item; no cross-tenant reference. |
| CAT-UC-08 | P0 | Owner/Manager | Change modifier or variant price | Version increases; new reads see new price; existing Order snapshots remain unchanged. |
| CAT-UC-09 | P0 | Authorized POS user | List/view sellable catalog for active branch | Returns only active category/item/variant/option combinations with server-resolved tenant scope. |
| CAT-UC-10 | P0 | Owner/Manager | List/view admin catalog, including inactive/archived entries | Pagination, allowlisted filters/sort, scope check before query. |
| CAT-UC-11 | P0 | Order workload | Resolve item, variant and selected modifiers for order creation | Returns current names, integer prices, rule result and catalog versions, or a typed rejection. |
| CAT-UC-12 | P0 | Catalog worker | Publish change/availability events | State change and outbox record commit together; duplicate delivery is safe for consumers. |
| CAT-UC-13 | P1 | Owner/Manager | Create/edit/disable fixed-price combo | Components are item/variant/modifier selections; no nested combo; required components must be sellable. |
| CAT-UC-14 | P1 | Owner/Manager | Set/remove current branch price override for a variant | Override belongs to an Identity branch in the same tenant; fallback is tenant variant price. |
| CAT-UC-15 | P1 | Owner/Manager | Edit item description and normalized tags | Tag slug is unique within tenant; AI projection receives versioned metadata. |

## Business Rules

### Categories and Items

1. Category hierarchy is flat in P0. Item has exactly one primary category, and both records must share `tenant_id`.
2. Category may be archived only when it has no non-archived items; otherwise return `409 conflict` and require items to be moved or archived first. No hard delete in P0.
3. Item lifecycle: `ACTIVE ↔ INACTIVE`, and either state → `ARCHIVED`. `ARCHIVED` is terminal. SKU remains reserved within tenant after archive.
4. `ACTIVE` item must have exactly one active default variant and at least one active variant. Create an inactive draft in a single transaction if the required sellable configuration is incomplete; activating an incomplete item returns `409`.
5. Disabling/archiving the last active variant, or the default variant without selecting a replacement in the same transaction, returns `409` for an active item.
6. POS sellability requires active category, item and variant. Item-level `INACTIVE`/`ARCHIVED` hides all variants; inactive variant hides only itself. Catalog enablement is distinct from Inventory stock availability. The consuming Order path must also validate current stock/reservation rules in Inventory.
7. P0 catalog status is global to tenant. Manager edits are audited as tenant-wide effects even when the manager is assigned to one branch.

### Variants, Prices and Modifiers

8. Every item has at least one variant, including a `STANDARD` variant for unsized items. Order selection must resolve a concrete `variantId` before pricing; old nullable `variantId` input maps to the default variant only when the item has exactly one active variant. New clients must send `variantId`.
9. P0 money is `BIGINT` in integer VND, with `currency = VND`. Variant base price and modifier surcharge are nonnegative; Catalog does not implement discounts or negative modifier deltas in P0. Order owns VAT/rounding and final price snapshot.
10. Modifier options belong to exactly one group. A group may attach to many items; an option can be selected only through a group attached to the chosen item.
11. Group defaults contain `minSelections` and `maxSelections` with `0 ≤ min ≤ max ≤ active option count`; optional means `min = 0`, required means `min ≥ 1`. Item-group links may override min/max and display order. Effective rule is link override if present, else group default.
12. Each modifier option may be selected at most once per item unit in P0; duplicate IDs are rejected. Per-option quantities and repeated topping portions are outside P0. Selection count is per group, and every selected option must be active.
13. Deactivating an option/group is rejected if it would make an ACTIVE item's required effective minimum impossible. Alternatively, the same transaction may deactivate the affected item; silent invalid configuration is prohibited.
14. Price and configuration changes use optimistic versions. A stale `If-Match` returns `409`; an Order resolution reads a coherent committed Catalog state and returns versions used for its snapshot.

### P1 Extensions

15. Combo contains no combo child. Each required component resolves an active item/variant and valid modifier selection. Fixed combo price is a nonnegative integer VND; Order snapshots combo structure and price.
16. A combo becomes non-sellable when any required component is not sellable. Inventory stock remains a separate fulfillment check.
17. Branch price override is unique for `(tenant_id, branch_id, variant_id)` and current only. An override can be removed to revert to tenant price. Branch must be ACTIVE and belong to the tenant; Catalog stores its ID, not an Identity DB FK.
18. Item description is optional text, bounded and sanitized. Tag has tenant-unique normalized slug and display name; tags are reused across items. AI cold-start processing is downstream and can arrive after the metadata event.

## Integration and API Requirements

| Boundary | Requirement |
|---|---|
| Identity | Verify Cognito JWT at gateway/service and resolve current tenant/branch/permission through Identity internal authorization; fail closed on unavailable authorization. |
| Public REST | `/api/v1/catalog` routes; OpenAPI 3.1; standard success/error envelope; `X-Correlation-Id`, `Idempotency-Key` for retryable mutations, `If-Match` for versioned updates. |
| Pagination | Admin item/category/group lists use offset pagination (`page`, `limit`, `total`, `totalPages`), default 20/max 100. Scope predicates precede pagination. |
| POS read cache | Short-lived tenant-scoped ETag/`If-None-Match`; cache key includes tenant, branch context and catalog version. Authorization is checked before `304`. |
| Order | Private bulk validation/price resolution should accept item, variant and modifier IDs plus tenant/branch context and return names, prices, version and typed rejection. Order stores immutable snapshots. |
| Inventory | Inventory owns recipes/stock. Catalog events carry stable item/variant IDs and state changes; no cross-service SQL join or DB FK. |
| Recommendation/Reporting | EventBridge/SQS consume versioned Catalog events with at-least-once delivery, deduplication, retry/DLQ and projection freshness. |
| Audit | Sensitive menu management changes include actor, scope, before/after summary and correlation ID without secrets or unneeded PII. |

Catalog's effective sellability is menu enablement. POS/Recommendation may additionally hide stock-unavailable items using Inventory data; Order makes the final fulfillment decision. A stale Recommendation projection cannot authorize an Order.

### Parent Contract Amendments Required

| Parent statement | Service decision | Required follow-up |
|---|---|---|
| Cashier may call `POST /catalog/items/{itemId}/availability` | Cashier is read-only for Catalog; Owner/Manager only. | Amend parent endpoint auth matrix before implementation. |
| `catalog_items.category` is a text label | Category is a tenant-owned entity and item stores `category_id`. | Retain category display label in read DTO as needed; update schema contract. |
| Flat `item_modifiers` relationship | Reusable group plus item-group link owns selection rules. | Update data/API schema; keep `modifierIds` concept at Order boundary. |
| `OrderLineInput.variantId` nullable | New Order clients supply a concrete variant; old omission resolves only for a single active variant. | Record this validation in Order contract before implementing Order. |
| Parent P2 lists AI cold start | P1 Catalog only supplies description/tags and metadata events. | AI cold-start algorithm remains downstream/P2 unless separately approved. |

## Non-Functional Requirements

These are demo targets, not production SLAs.

| Category | Requirement | Target |
|---|---|---|
| Performance | POS catalog list/detail | p95 ≤ 500 ms under parent demo profile with representative seeded menu; cache may help but correctness takes precedence. |
| Performance | Order bulk resolution | p95 ≤ 300 ms for an order of up to 20 lines and 50 modifier selections, excluding Inventory. |
| Scalability | API/worker | Stateless API and independently scalable outbox publisher; bounded DB connection pool. |
| Consistency | Catalog mutation | Item/variant/modifier state and outbox event commit atomically; reads never see half-configured active items. |
| Security | Isolation | Every read/write and association validates tenant ownership at service and repository layers; negative BOLA/IDOR tests. |
| Security | Input | Validate names/SKU/price/rules/IDs; parameterized SQL; sanitized description/note and safe error messages. |
| Reliability | Idempotency | Same key/hash replays original result; different hash gives `duplicate_request` 409; event consumers deduplicate by event ID. |
| Observability | Tracing | Structured JSON logs with correlation ID, tenant, actor, resource, outcome and duration; no JWT/secret/PII. |
| Observability | Alerts | Track failed mutations, stale price rejection, outbox lag, DLQ depth, catalog read p95 and authorization failures. |
| Deployment | Reproducibility | Docker Compose local, PostgreSQL per service and AWS ECS Fargate/EventBridge/SQS target. |
| Maintainability | Contract discipline | OpenAPI/AsyncAPI and migrations validated in CI; backwards compatible P1 extensions. |

## Data and Constraints for Architecture

- Java 21/Spring Boot 3.x, PostgreSQL service-owned database, UUID v7 identifiers, UTC `TIMESTAMPTZ`, `BIGINT` integer VND.
- Core entities: categories, catalog items, item variants, modifier groups, modifier options, item-group links, outbox and idempotency records. P1: combos/components, branch variant prices, tags and item-tag links.
- Enforce tenant consistency with composite keys/FKs inside Catalog DB. No FK to Identity, Order, Inventory or Recommendation databases.
- Archive preserves item/variant/modifier IDs needed by historical Order and Inventory references.
- Plan indexes for tenant/category/status/sort/SKU, item variants, group links, modifier selections, event publisher and branch override lookup.
- No partitioning expected for normal Catalog tables at MVP scale; review outbox growth/retention separately.
- All schema columns need descriptive migration comments/remarks. Migration must be forward compatible with a repair/rollback plan and deterministic local seed data.

## Acceptance Tests

1. Owner and active Manager can create/edit tenant-wide catalog; Cashier/Kitchen writes return `403`.
2. A Manager with access only in tenant A cannot read/write tenant B; revoking the manager membership denies the next request with the old JWT.
3. Category archive with non-archived items returns `409`; after items move/archive it succeeds without deleting history.
4. Item creation persists a default variant and valid integer price atomically; duplicate tenant SKU returns `409`.
5. Disabling the last active/default variant of an active item fails unless replacement/state transition is committed together.
6. Inactive item/variant/modifier is excluded from new POS selections; authorized historical lookup remains available internally.
7. Modifier group reuse yields each item's effective min/max rule; under/over selection, duplicate/foreign/inactive option IDs fail.
8. A price update changes subsequent Order resolutions while an existing Order snapshot remains unchanged.
9. Missing `variantId` resolves only when exactly one active variant exists; ambiguity fails with a validation error.
10. Catalog-active but stock-unavailable item is rejected by Order/Inventory fulfillment, even if POS/Recommendation view is stale.
11. Same idempotency key/hash does not duplicate mutation/outbox; same key/different hash returns `409`.
12. P1 branch price override affects only the selected branch and falls back to tenant price when removed.
13. P1 combo with inactive required component is non-sellable; nested combo is rejected.
14. P1 tag normalization prevents duplicate tenant slug; metadata event carries a version and no sensitive data.
15. Contract and security tests cover cross-tenant IDs in every association, stale `If-Match`, missing authorization and outbox retry/DLQ.

## Decisions Carried to Architecture and Contracts

1. Exact category/item/group table constraints and transaction boundary for active-item invariants.
2. Exact Order bulk resolver request/response, availability reason codes and version semantics.
3. Whether category archive/price updates publish item-level invalidation events or aggregate version events.
4. ETag generation, cache invalidation and read path against current authorization.
5. P1 combo composition and branch override API/schema details.

## Domain Skills Referenced

- **spec-reviewer:** converted eight Vision warnings into explicit policies and parent contract amendments.
- **api-design:** REST/OpenAPI, role checks, pagination, conditional GET, optimistic concurrency and standard errors.
- **database-design:** relational tenant constraints, indexes, UUID v7, integer money, column remarks and migration planning.
- **log-processing:** structured correlation/audit signals with sensitive-data redaction; no logging code was changed.

## Approval

- **Status:** Approved by user
- **Phase:** Requirements Discovery
- **Next phase after approval:** Architecture Design
