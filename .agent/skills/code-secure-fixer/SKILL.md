---
name: code-secure-fixer
description: >
  Skill for analyzing and fixing security vulnerabilities from GitLab CI Security Scanner report files (semgrep SAST, gitleaks Secret detection) or similar tools.
  ALWAYS use this skill when the user: provides a security report file (.json), wants to fix semgrep/gitleaks findings, wants to analyze security scan results, mentions "fix secure", "fix security", "security report", "security scan", "security vulnerability", "fix secret", "fix hardcode", "handle vulnerability", "analyze scan report", "sast", "gitleaks findings", "semgrep findings".
  This skill guides execution step by step, with confirmation gates before fixing high-impact issues.
---

# code-secure-fixer — Security Vulnerability Analysis & Fix

## Overview

This skill helps analyze report files from security scanners (semgrep, gitleaks, ...), plan and execute fixes in priority order from lowest to highest risk. All changes with high or critical impact must be confirmed with the user before proceeding.

---

## Step 1 — Request & Validate Report File

If the user has not provided a report file, ask:
> "Do you have a report file from a security scanner? Please provide the file path (e.g., `/path/to/report.json`)."

Only proceed once a file is available. After receiving the file, validate:
- Does the file exist?
- Can it be read and parsed as JSON?
- Does it contain the required keys? (e.g., `Findings`, `Scanners` for GitLab format; or `vulnerabilities` for other formats)

If the file is invalid → report the specific error and request the file again. Do not proceed until a valid file is provided.

---

## Step 2 — Analyze High-Level Overview

Read and display:

**2.1 Repository/commit information:**
- Repo name, scanned branch, commit SHA, scan timestamp

**2.2 Branch check gate — STOP here:**

Before executing any fix (every level), run `git branch --show-current` and compare against the **most recent `git branch --show-current` result found in the conversation history**. If no previous result exists in context → this is the first check; record it as the baseline and continue normally.

If they **differ**, alert the user immediately:

> ⚠️ **Branch changed mid-session!**
> - Branch at session start: `[session branch]`
> - Current working branch: `[current git branch]`
>
> Any fixes applied from this point will land on **`[current branch]`**, not the branch you started with.
> Do you want to:
> 1. **Continue** — apply fixes on the current branch as-is
> 2. **Restart** — I will stop here so you can checkout the correct branch, then begin again

**MUST wait for user's answer before proceeding.** Do not proceed until the user explicitly chooses option 1 or 2.

If the user chooses option 2 → stop completely. Do not execute any fixes.

If branches **match** → continue normally.

---

**2.3 Scanner table:**

```
┌──────────┬────────┬──────────────────────────────────────────────────────────────┐
│ Scanner  │  Type  │ What it detects                                              │
├──────────┼────────┼──────────────────────────────────────────────────────────────┤
│ gitleaks │ Secret │ Secrets/credentials committed to git history                 │
├──────────┼────────┼──────────────────────────────────────────────────────────────┤
│ semgrep  │ SAST   │ Security logic flaws in source code (injection, crypto, etc.) │
└──────────┴────────┴──────────────────────────────────────────────────────────────┘
```

Display only the scanners actually present in the report.

---

## Step 3 — Statistics & Finding Grouping

**3.1 Summary table:**

```
┌──────────┬──────────┬──────────┬──────┬────────┐
│ Scanner  │ # Issues │ Critical │ High │ Medium │
├──────────┼──────────┼──────────┼──────┼────────┤
│ semgrep  │ ...      │ ...      │ ...  │ ...    │
├──────────┼──────────┼──────────┼──────┼────────┤
│ gitleaks │ ...      │ ...      │ ...  │ ...    │
├──────────┼──────────┼──────────┼──────┼────────┤
│ Total    │ ...      │ ...      │ ...  │ ...    │
└──────────┴──────────┴──────────┴──────┴────────┘
```

**3.2 Group findings by type:**

Consolidate findings with the same rule/pattern into one group. For each group display:

```
🔴 Group N — [Issue Name] ([X issues Severity]) — [scanner]
- Code status:  Active ⚠️ | Commented ✅ | Non-source 📄
- Impact scope: This file only | Cross-module | System-wide
- Root cause: [brief explanation of why this is a security issue]
- File list:
  - path/to/File.cs (line 10, 20)
    Fix approach: [prioritize report's recommendation; suggest better solution if available with explanation]
  - path/to/File2.cs (line 14)
    Fix approach: ...
```

**Group color coding:**
- 🔴 Critical/High
- 🟡 Medium
- 🟢 Low/Info

**Analysis rules:**
- Commented-out code still needs to be fixed if the scanner detects it (gitleaks scans git history)
- Non-source files (.txt logs, .json test data, .ps scripts) are typically Step 1 (delete file)
- Clearly distinguish: credentials in appsettings (config) vs credentials in .cs files (hardcode)

**3.3 Confirmation gate — STOP here:**

Before proceeding, ask the user:
1. Are there any findings outside the normal scope (e.g., Framework/, vendor directories, FE app)? If so → ask whether to fix those as well.
2. "Does the above plan look reasonable? Do you want to skip any groups or adjust the order?"

Only proceed to Step 4 after user confirmation.

---

## Step 4 — Propose Fix Order

Present the fix order from lowest to highest risk. Map the finding groups analyzed in Step 3 to the levels below:

| Level | Fix Type | Example | Confirm Required |
|-------|----------|---------|-----------------|
| 1 | No code change — delete/gitignore unnecessary files | Log files, test data JSON/XML, unneeded .ps scripts | No |
| 2 | Fix configuration files | `appsettings.json`, `appsettings.Development.json`, `.env`, `application.yml` | No |
| 3 | Fix hardcode/comments in code — low impact | Private key in comments, API key in doc comment, password in commented-out class | No |
| 4 | Fix active hardcode — medium impact | ClientSecret hardcode → read from `_configuration[...]`, missing antiforgery, JWT not validating lifetime | No |
| 5 | Fix SSL/CORS bypass — medium-high impact | `TrustAllCert`, `ServerCertificateCustomValidationCallback = true`, wildcard CORS `AllowAnyOrigin()`, hostname verifier bypass | **Ask per item** |
| 6 | Fix algorithms/encryption — high impact | Change cipher mode (CBC → GCM), replace hash mechanism, fix authentication | **MUST confirm** |
| 7 | System-level fix — critical impact | Library versions with CVE, framework configuration, git history rewrite | **MUST confirm** |

**After user confirms — export `fix-code-secure-plan.md`:**

1. Run `git branch --show-current` → record as `session-branch`
2. Write `fix-code-secure-plan.md` to the project root using the template at `references/fix-code-secure-plan-template.md` (in this skill's directory)
3. Populate all placeholders: scan info (from Step 2), scope decisions (from Step 3), credentials-to-rotate (from gitleaks findings), per-level `[ ]` task lists for every file/finding
4. Inform user that `fix-code-secure-plan.md` has been generated

**Context gate — STOP here:**

> ⚠️ **Context warning:** This session has already analyzed all findings. Executing fixes here risks running out of context mid-session.
>
> **Recommendation:** Open a new context and say:
> *"Follow `fix-code-secure-plan.md` to fix security issues"*
>
> The plan file includes full execution instructions for Levels 1–7 in the **Agent Execution Instructions** section.
>
> Do you want to:
> 1. **Continue here** — execute fixes in this context (risk: may hit context limit)
> 2. **Open new context** — stop here; start a new session referencing `fix-code-secure-plan.md`

**MUST wait for user's answer before proceeding.**
- If user chooses option 2 → stop completely. Do not execute any fixes.
- If user chooses option 1 → proceed to Step 5.

---

## Step 5 — Execute Fixes

**Before starting — read `fix-code-secure-plan.md`:**

1. Read `fix-code-secure-plan.md` in the project root directory
2. Run `git branch --show-current` and compare with `session-branch` from the frontmatter
   - If they **differ** → show the branch change warning (Step 2.2 format) and wait for user's choice before proceeding
   - If they **match** → proceed
3. Use the task lists in `fix-code-secure-plan.md` as the authoritative checklist for this session

**After completing each task:** update `[ ]` → `[x]` in `fix-code-secure-plan.md`. When all levels are done, set `status: completed` in the frontmatter.

Execute in strict order from Level 1 → Level 7. For each level:

1. Announce start: "Fixing Level [N] — [group name]..."
2. Execute the fix
3. Report results: number of files changed, what was changed
4. Ask: "Continue to Level [N+1]?"

**Key rules when fixing:**

- **Level 1 — Delete files:** DO NOT delete without confirmation. List all files to be deleted, ask user to confirm each file or all at once.
- **Level 2 — Config files:** Replace sensitive values with `""`. If config files have multiple environments (Development/Production), handle all of them.
- **Level 3 & 4 — Hardcode in code:**
  - Secrets → read from `IConfiguration` / environment variables / secret manager
  - Keys in comments/commented code → delete or replace with placeholders like `{your-key-here}`
- **Level 5 — SSL/CORS bypass:** For each finding, present:
  - What the bypass does and its risk (e.g., TrustAllCert → enables MITM attacks)
  - Whether it is intentional (e.g., dev/test environment only) or a real bug
  - Options: `[F]ix` / `[I]gnore this` / `[I]gnore all`
  If user chooses "Ignore all" → skip the entire level and add a `// nosemgrep` comment with reason to each affected line.
- **Level 6 — Algorithms:** Confirm first. Clearly explain if this is a breaking change for encrypted data. Suggest a migration plan if needed.
- **Level 7 — System:** Confirm first. Especially for git history rewrites, emphasize the need for full team coordination.
- **False positives:** If a finding is a false positive (e.g., a key is just an example, not real), explain to the user and add a suppression comment with reason (`// nosemgrep: [rule-id] - [reason]`).

---

## Step 6 — Summary & Notes

**6.1 Work summary table:**

```
┌───────┬──────────────────────────────────────┬──────────────┬────────────┐
│ Level │ Description                          │ Files Fixed  │ Status     │
├───────┼──────────────────────────────────────┼──────────────┼────────────┤
│ 1     │ Delete log/test files                │ X            │ ✅ Done    │
│ 2     │ appsettings.json (credentials)       │ X            │ ✅ Done    │
│ 3     │ Remove keys in comments/commented    │ X            │ ✅ Done    │
│ 4     │ Hardcode → read from config          │ X            │ ✅ Done    │
│ 5     │ Fix SSL/CORS bypass                  │ X            │ ✅/⏭️ Skip │
│ 6     │ Fix encryption algorithms            │ X            │ ✅/⏭️ Skip │
│ 7     │ Git history / system                 │ X            │ ✅/⏭️ Skip │
└───────┴──────────────────────────────────────┴──────────────┴────────────┘
```

**6.2 ⚠️ MANDATORY notes after fixing:**

**ROTATE CREDENTIALS** — Most important:
> Any secret that has ever been committed to the git repo (even once) must be considered compromised and **MUST be revoked and regenerated immediately**, even if already removed from the current codebase.

List the specific credentials to rotate from the findings, for example:
- `ClientSecret = "hEUPvNX3..."` at `AccountService.cs:50` → Rotate OAuth client secret
- `X-API-KEY: "7fe4cb1a-..."` at `ZipInvoiceClientService.cs:23` → Rotate API key

**Update `.gitignore`:**
```
# Add sensitive files to .gitignore
appsettings.Development.json
appsettings.local.json
*.local.json
.env
*.log
```

**Notify the team:** If there are breaking changes (cipher change, rotating a shared secret), notify all team members to update their local environments.

---

## Step 7 — Git History Cleanup (Only When gitleaks Findings Are Present)

> ⚠️ gitleaks scans the entire git history. Secrets removed from the current codebase still exist in old commits and will continue to trigger findings.

**MUST confirm with user before proceeding** because:
- This operation REWRITES the entire git history
- Affects all team members
- Requires a force push to remote → needs permission and team coordination
- Once rewritten and pushed, it is very difficult to undo

**Recommended tools:**

```bash
# Use git-filter-repo (official, recommended by Git)
pip install git-filter-repo

# Remove secret from entire history
git filter-repo --replace-text <(echo 'OLD_SECRET_VALUE==>REDACTED')

# Then force push (CONFIRM WITH TEAM FIRST)
git push origin --force --all
```

Or use **BFG Repo Cleaner** for large repos:
```bash
# Create a file containing the list of secrets to remove
echo "SECRET_VALUE" > secrets.txt
java -jar bfg.jar --replace-text secrets.txt
git reflog expire --expire=now --all && git gc --prune=now --aggressive
```

After completion, instruct all team members to run:
```bash
git fetch --all
git reset --hard origin/<branch>
```
