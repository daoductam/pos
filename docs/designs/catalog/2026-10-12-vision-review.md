Started at: 2026/10/12 16:10:00
Finished at: 2026/10/12 16:10:00
Total time: 0 minutes
---

# Spec Sentinel Report

**Spec:** `docs/designs/catalog/2026-10-12-vision.md`  
**Date:** 2026-10-12  
**Status:** ⚠️ Warnings (Proceed with Caution)

## Findings

### Warnings

| # | Category | Finding | Risk | Recommendation |
|---|---|---|---|---|
| 1 | Authorization | Catalog P0 is tenant-wide, but Vision says a manager administers it “within assigned branch”. A tenant-wide mutation changes the menu visible at other branches. | A branch manager could unintentionally alter another branch's menu. | In Requirements, define whether an assigned manager may edit tenant-wide catalog and which commands require owner. Explicitly distinguish authorization scope from the effect scope of a mutation. |
| 2 | Parent contract conflict | Parent Catalog endpoint `POST /items/{itemId}/availability` allows cashier, while Vision says Cashier is read-only. | Different teams could implement incompatible permissions. | Select one policy in Requirements and update the parent contract or record an explicit service-level override before coding. |
| 3 | Parent data shape | Parent catalog contract stores category as a text label and uses flat `item_modifiers`; Vision selects category entities and reusable modifier groups. | Order/client integrations may assume the old response shape. | Preserve public item IDs and version the new category/group response fields; document the parent contract delta in Phase 4. |
| 4 | Order pricing boundary | Vision requires variant pricing and Order snapshots, but the parent `OrderLineInput.variantId` is nullable. | Order may resolve an ambiguous price if a client omits the variant. | Require an explicit variant ID at the Catalog–Order boundary or define deterministic default variant resolution. |
| 5 | Sellability semantics | “ACTIVE item has at least one ACTIVE variant” is a success criterion, but disabling the last variant could violate it. | State transitions may leave an ACTIVE item that cannot be sold. | Define an atomic invariant: reject disabling the last active variant or change item status in the same transaction. Include archive/category behavior. |
| 6 | Inventory and recommendation | Catalog availability means menu enablement; Inventory controls stock. Vision does not yet state precedence when an item is active but out of stock. | POS and Recommendation may show an item that cannot be fulfilled. | Define effective sellability as a composition of Catalog state and Inventory stock at the consuming boundary; specify stale projection behavior. |
| 7 | Modifier quantity | `minSelections`/`maxSelections` are named, but duplicate modifier IDs, per-option quantity and negative price deltas remain undefined. | POS and Order can calculate different totals. | Fix modifier selection and price rules, including currency and validation ownership, in Requirements/Contracts. |
| 8 | P1 extensions | Combo component model and branch price are high level; tag data for AI cold start has no freshness/version contract yet. | P1 could produce incompatible Order and Recommendation payloads. | Keep P1 isolated; define exact extension contracts and consumer expectations in Phase 4. |

## Cross-Impact Analysis

| Dimension | Finding | Risk Level | Recommendation |
|---|---|---|---|
| **Shared Logic** | No application source exists. Planned consumers are Angular POS/Dashboard, Order, Inventory, Recommendation and Reporting. | 🟡 Medium | Define Catalog-owned validation and consumer snapshots/contracts before implementation. |
| **Data/State** | New normalized categories, item variants and reusable modifier groups refine parent conceptual Catalog schema. | 🟡 Medium | Define tenant-owned FKs/unique keys, lifecycle transitions and migration compatibility. |
| **API Contracts** | Parent has five Catalog endpoints; Vision needs category, variant and modifier group APIs and changes the meaning of availability writes. | 🟡 Medium | Keep `/api/v1/catalog` conventions and document every parent contract change explicitly. |
| **Flow Integrity** | POS menu read → Order selection/snapshot → Inventory reservation → Recommendation projection is coherent, but variant resolution and stock precedence remain open. | 🟡 Medium | Specify a current-price/sellability validation path, failure behavior and snapshot timing. |
| **Cross-Cutting** | Tenant isolation, authorization, caching and event delivery affect every Catalog read/write. | 🔴 High | Enforce current Identity scope at service and repository layers; cache only non-sensitive scoped reads and invalidate by version/event. |

## Instruction Compliance

| Dimension | Verdict | Deviation (if any) | Recommendation |
|---|---|---|---|
| **Conventions** | ✅ | — | Continue parent REST/OpenAPI, UUID v7, integer money, error envelope and design folder conventions. |
| **Architecture** | ⚠️ | Parent flat category/modifier shape and cashier availability grant differ from Vision. | Record service-level decisions and parent contract amendments in Requirements/Contracts. |
| **Testing** | ⚠️ | Vision lists success measures but not a negative test matrix. | Add RBAC, cross-tenant, last-variant, modifier validation, stale price and event retry tests. |

## Review Context and Decision

- Phase 0 skipped — reusing the approved parent POS and Identity designs plus the current Vision discussion.
- The repository has design files but no Catalog implementation or call sites to trace.
- Project rules in `.agent/rules/code-quality.md` and `.agent/rules/security.md` apply, especially tenant ownership checks on every data access.
- No blocker prevents Requirements Discovery. The eight warnings must be resolved or explicitly carried to Architecture/Contracts before implementation.
- Per `.agent/workflows/design.md`, this warning verdict requires user confirmation before continuing to Requirements Discovery.

