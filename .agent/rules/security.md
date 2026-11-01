---
trigger: always_on
---

# Security Guidelines

## Mandatory Security Checks

Before ANY commit:
- [ ] No hardcoded secrets (API keys, passwords, tokens)
- [ ] All user inputs validated
- [ ] SQL injection prevention (parameterized queries)
- [ ] XSS prevention (sanitized HTML)
- [ ] Authentication/authorization verified
- [ ] Error messages don't leak sensitive data

## Secret Management

- NEVER hardcode secrets in source code
- ALWAYS use environment variables or a secret manager
- Validate that required secrets are present at startup
- Rotate any secrets that may have been exposed

## Multi-Tenant Security (Default ON)

When the project uses multi-tenant architecture (common in most internal projects):

- [ ] **Ownership/tenant validation** on EVERY data access — never trust client-provided IDs alone
- [ ] **IDOR prevention** — verify requesting user/tenant owns the resource before returning data
- [ ] **Data isolation** — all queries MUST be filtered by tenant context (tenant ID, org ID, etc.)
- [ ] **Cross-tenant access** — explicitly denied and logged as suspicious activity
- [ ] **Shared resources** — clearly marked and access-controlled separately from tenant-scoped data
- [ ] **Batch/bulk operations** — validate ALL items in the batch belong to the requesting tenant
- [ ] **URL/path parameters** — IDs in URLs (e.g., `/orders/{id}`) must be verified against tenant scope

> **Detection:** If the project has tenant/org/company ID in its data models, auth tokens, or request context — multi-tenant rules apply automatically.

## Security Response Protocol

If security issue found:
1. STOP immediately
2. Fix CRITICAL issues before continuing
3. Rotate any exposed secrets
4. Review entire codebase for similar issues