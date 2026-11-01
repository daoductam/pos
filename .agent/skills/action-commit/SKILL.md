---
name: action-commit
description: "You MUST use this when user requests to commit code, trigger when user chat 'create commit', 'tao commit cho toi'"
allowed-tools: Read, Grep
---

# Objective: Standardize Code Commit Messages

Follow these conventions based on the Git Flow architecture to ensure consistency across branches and environments.

## 1. Branch-to-Prefix Mapping

Identify the current branch and extract the prefix according to the following rules:

| Branch Category | Branch Name Pattern         | Extracted Prefix        | Environment Context |
| :-------------- | :-------------------------- | :---------------------- | :------------------ |
| **Feature**     | `feature/[Jira-ID]-name`    | `[Jira-ID]`             | DEV/SIT             |
| **Hotfix**      | `hotfix/[Jira-ID]-name`     | `[Jira-ID]` or `hotfix` | PROD/Merge back     |
| **Release**     | `release/*` or `releases/*` | `release`               | UAT                 |
| **Main**        | `master` or `main`          | `master`                | PROD                |
| **Common**      | `common`                    | `common`                | Shared Logic        |

> [!TIP]
> **Jira-ID Extraction:** If the branch name contains a structured ID (e.g., `VONB-13660`), always prioritize this as the prefix. If no ID is found (e.g., `feature/ui-update`), use the full branch name or the category (e.g., `feature`).

## 2. Commit Message Formatting

The final commit message MUST follow the format: `[Prefix]: [Summary]`

- **Summary length:** Keep it concise, ideally under 15 words.
- **Content:** Ensure the text accurately describes the functional changes.
- **Excluded Files:** Never mention or trigger commits based purely on changes in:
  - `README.md`
  - `.agents/**` (Agent-specific logic)
  - `.gitignore`
  - `.gemini/**`

## 3. Special Cases & Workflow Rules

- **Merges:** When committing a merge on `master` or `develop` (e.g., merging a hotfix or release), the prefix should be the target branch name (`master:` or `develop:`).
- **Multiple IDs:** If a branch involves multiple Jira IDs, use the primary one or the first one listed.
- **Special Branches:** For branches like `common` or `chore`, use the branch name as the prefix.

## 4. Examples

| Scenario          | Branch Name                  | Prefix       | Final Commit Message                                   |
| :---------------- | :--------------------------- | :----------- | :----------------------------------------------------- |
| **New Feature**   | `feature/VONB-13660-deposit` | `VONB-13660` | `VONB-13660: implement deposit logic and validation`   |
| **Urgent Fix**    | `hotfix/VONB-14500-leak`     | `VONB-14500` | `VONB-14500: fix memory leak in socket connection`     |
| **Release Prep**  | `release/v1.2.0`             | `release`    | `release: update version to 1.2.0 and final UAT fixes` |
| **Direct Master** | `master`                     | `master`     | `master: emergency config update for production`       |

## 5. Co-Authorship

When AI Agent contributes to a commit, add the following trailer to identify AI co-authorship:

```
Co-authored-by: AI Agent <agent@ai-kit>
```

This follows GitHub/GitLab co-author convention and displays a "Co-authored" badge on PRs and commits.

**Usage:** Append the co-author trailer after the commit message body (if any) and before the final `---` or end of commit.

**Example with body:**
```
[VONB-13660]: implement user authentication

- Add login endpoint with JWT validation
- Implement password hashing with bcrypt
- Add session management

Co-authored-by: AI Agent <agent@ai-kit>
```

---
