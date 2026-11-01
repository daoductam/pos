---
report: {report-path}
generated-at: {timestamp}
session-branch: {session-branch}
status: in-progress
---

# Fix Plan — {repo-name}

## Scan Info
| Field | Value |
|-------|-------|
| Report | `{report-path}` |
| Scanned branch | `{scanned-branch}` |
| Commit | `{commit-sha}` |
| Scan timestamp | `{scan-timestamp}` |
| Generated at | `{generated-at}` |
| Working branch | `{session-branch}` |

## Scope Decisions
- {scope-decision-1}

## Risk Summary
| Level | Description | # Tasks |
|-------|-------------|---------|
| 1 | Delete unnecessary files | {n} |
| 2 | Fix config files | {n} |
| 3 | Fix secrets in comments | {n} |
| 4 | Fix active hardcode | {n} |
| 5 | Fix SSL/CORS bypass | {n} |
| 6 | Fix algorithms/encryption | {n} |
| 7 | System-level fix | {n} |

## Credentials to Rotate
> ⚠️ Any secret ever committed to git MUST be revoked and regenerated immediately.

- [ ] `{secret-value}` at `{file}:{line}` → {rotate-action}

---

## Level 1 — Delete Unnecessary Files
- [ ] `{file-path}`

## Level 2 — Fix Config Files
- [ ] `{file-path}` — replace `{key}` with `""`

## Level 3 — Secrets in Comments/Commented Code
- [ ] `{file-path}:{line}` — {description}

## Level 4 — Active Hardcode
- [ ] `{file-path}:{line}` — {description}

## Level 5 — SSL/CORS Bypass
- [ ] `{file-path}:{line}` — {description}

## Level 6 — Algorithms/Encryption
- [ ] `{file-path}:{line}` — {description}

## Level 7 — System-level
- [ ] {description}

---

## Agent Execution Instructions
> **For new agents starting a fix session from this file** — follow the steps below exactly.

### Step 1 — Branch Check (MANDATORY before any fix)
1. Run `git branch --show-current`
2. Compare with `session-branch` in the frontmatter above
   - If they **differ** → warn the user:
     > ⚠️ Branch changed! Session branch: `{session-branch}` — Current: `{current}`
     > Continue on current branch? Or stop and checkout the correct branch?
     > **MUST wait for user's answer before proceeding.**
   - If they **match** → proceed

### Step 2 — Execute Levels in Order
Execute Level 1 → Level 7 strictly in order. For each level:
1. Announce: "Fixing Level [N] — [description]..."
2. Execute the fix
3. Update `[ ]` → `[x]` for each completed task in this file
4. Report results, then ask: "Continue to Level [N+1]?"

### Step 3 — Fix Rules per Level
- **Level 1 — Delete files:** List all files first, ask user to confirm before deleting
- **Level 2 — Config files:** Replace sensitive values with `""`; handle all environments (Dev/Prod)
- **Level 3 & 4 — Hardcode:** Secrets → read from `IConfiguration`/env vars; keys in comments → replace with `{your-key-here}`
- **Level 5 — SSL/CORS bypass:** For each finding, explain the risk, then offer:
  `[F]ix` / `[I]gnore this` / `[I]gnore all`
  If "Ignore all" → skip level, add `// nosemgrep` comment with reason to each affected line
- **Level 6 — Algorithms:** **MUST confirm first.** Explain if it is a breaking change. Suggest migration plan if needed
- **Level 7 — System:** **MUST confirm first.** For git history rewrite: coordinate full team, force push required
- **False positives:** Add `// nosemgrep: [rule-id] - [reason]` suppression comment

### Step 4 — Git History Cleanup (Level 7 — gitleaks only)
> ⚠️ gitleaks scans entire git history. Secrets removed from current code still exist in old commits.

**MUST confirm with user** — this rewrites history, affects all team members, requires force push.

```bash
# Option A: git-filter-repo (recommended)
pip install git-filter-repo
git filter-repo --replace-text <(echo 'OLD_SECRET_VALUE==>REDACTED')
git push origin --force --all

# Option B: BFG Repo Cleaner (for large repos)
echo "SECRET_VALUE" > secrets.txt
java -jar bfg.jar --replace-text secrets.txt
git reflog expire --expire=now --all && git gc --prune=now --aggressive
```

After completion, all team members must run:
```bash
git fetch --all
git reset --hard origin/<branch>
```

### Step 5 — Summary & Mandatory Notes
After all levels are done:
1. Set `status: completed` in the frontmatter of this file
2. Show summary table:

```
┌───────┬──────────────────────────────────────┬──────────────┬────────────┐
│ Level │ Description                          │ Files Fixed  │ Status     │
├───────┼──────────────────────────────────────┼──────────────┼────────────┤
│ 1     │ Delete log/test files                │ X            │ ✅ Done    │
│ 2     │ appsettings.json (credentials)       │ X            │ ✅ Done    │
│ 3     │ Remove keys in comments              │ X            │ ✅ Done    │
│ 4     │ Hardcode → read from config          │ X            │ ✅ Done    │
│ 5     │ Fix SSL/CORS bypass                  │ X            │ ✅/⏭️ Skip │
│ 6     │ Fix encryption algorithms            │ X            │ ✅/⏭️ Skip │
│ 7     │ Git history / system                 │ X            │ ✅/⏭️ Skip │
└───────┴──────────────────────────────────────┴──────────────┴────────────┘
```

3. **ROTATE CREDENTIALS** — remind user of the Credentials to Rotate checklist above
4. **Update `.gitignore`** if not already done:
   ```
   appsettings.Development.json
   appsettings.local.json
   *.local.json
   .env
   *.log
   ```
5. **Notify team** if there are breaking changes (cipher change, shared secret rotation)
