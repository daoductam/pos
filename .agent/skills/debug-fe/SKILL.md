---
name: debug-fe
description: "Performs structured UI debugging using Playwright to identify, reproduce, and fix runtime UI issues, verify user flows, and inspect layout or functional problems in web applications. Triggered when users report page-specific bugs, visibility/layout issues, broken functionality, or unexpected user flows."
---

# Debug UI Skill

Perform structured debugging on the running application UI using Playwright tools to identify, reproduce, and fix runtime-reproducible UI issues, verify user flows, and inspect layout or functional problems.

---

## Trigger Conditions

Use when the user reports:

- Bugs on specific pages/URLs.
- Component visibility or behavior issues.
- Need to test specific prompts or user flows.
- "White screens", missing elements, or broken layouts.
- Functional failures (e.g., buttons not working, messages not sending).

---

## Prerequisites (Playwright MCP Check) 🛠️

Confirm Playwright MCP is active before starting:

1.  **Check Tools:** Verify `mcp_playwright_browser_navigate` and related tools exist.
2.  **Config:** If missing, check `mcp-config.json` for `playwright` server.
3.  **Halt:** if not configured, stop and notify user: _"I need Playwright for this, but it's not configured. Please enable it in MCP settings."_

---

## Process Flow

```
[Prerequisite] Confirm Playwright MCP
         ↓
[0] Locate Code & Route (grep, router analysis)
         ↓
[1] Navigate & Initialize (mcp_navigate)
         ↓
[2] Reproduce (mcp_type, mcp_click)
         ↓
[3] Inspect (mcp_snapshot, mcp_console_messages)
         ↓
[4] Diagnose (Code Analysis + State)
         ↓
[5] Fix & Verify (Code Change + Rerun Step 2)
```

---

## Step 0 — Locate Function & Page

If the URL is unknown:

1.  **Search ID:** Use `grep_search` for UI text, function names, or IDs.
2.  **Verify Logic:** Use `view_file` to confirm the handler logic.
3.  **Find Route:** Map the component to a path in `src/router.tsx` or equivalent.

---

## Step 1 — Navigate & Initialize

- **Server Check:** Ask if the app is already running. If a domain is provided or mentioned earlier, skip `npm run dev`.
- **Run Dev:** Execute `npm run dev` only if no active environment is confirmed.
- **Navigate:** Use `mcp_playwright_browser_navigate` to the identified URL.
- **Snapshot:** Capture initial state with `mcp_playwright_browser_snapshot`.

---

## Step 2 — Reproduce Issue

Execute user steps using Playwright:

- **Input:** `mcp_playwright_browser_type` or `mcp_playwright_browser_fill_form`.
- **Action:** `mcp_playwright_browser_click` or `mcp_playwright_browser_press_key`.
- **Wait:** Use `mcp_playwright_browser_wait_for` for async updates (e.g., AI streaming).
- **Final Snapshot:** Capture state once the issue is reproduced.

---

## Step 3 — UI & Console Inspection

### 3a. Visibility & DOM

- Check snapshot for element presence.
- Hidden? Use `mcp_playwright_browser_evaluate` to check styles (`display: none`, `z-index`, clipping).

### 3b. Logs & Network

- **Console:** `mcp_playwright_browser_console_messages` for JS errors.
- **Network:** `mcp_playwright_browser_network_requests` for failed API calls/assets.

---

## Step 4 — Identify Root Cause

Analyze UI behavior against source code:

- **State Loss:** Unexpected re-renders, unmounts, or state resets in the UI/component/page.
- **Logic:** Conditional rendering or branching logic (if/else, switch/case, etc.) handling current state hoặc dữ liệu không đúng.
- **Store:** Global/shared state updates hoặc truyền dữ liệu giữa các phần UI không đúng (bất kể framework nào).

---

## Step 5 — Implement & Verify Fix

1.  **Plan:** Describe the fix in `implementation_plan.md`.
2.  **Apply:** Modify code.
3.  **Verify:** Rerun **Step 2**; confirm fix and no new console errors.

---

## Artifacts: RED / GREEN / REFACTOR

- **RED**: Record logs or screenshots when the issue is reproduced (baseline fail).
- **GREEN**: Record logs or screenshots after the issue is fixed (baseline pass).
- **REFACTOR**: For major structural changes, document the refactor solution and rationale.

Artifacts should be saved in the `artifacts/debug-fe/` directory or attached to pull requests for easier verification and review.

---

## Anti-Patterns

| Anti-pattern              | Why it is wrong                                                     |
| :------------------------ | :------------------------------------------------------------------ |
| Assuming a fix works      | Runtime behavior often differs from static analysis.                |
| Ignoring Console Warnings | Warnings often precede errors or indicate Hook misuse.              |
| Testing only "Happy Path" | Most bugs occur in edge cases or rapid interactions.                |
| Using `waitForTimeout`    | Use `mcp_playwright_browser_wait_for` with text/element conditions. |
