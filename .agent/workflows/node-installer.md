---
name: node-installer
description: "A utility skill to deeply evaluate a project's Node.js requirement via dependency analysis, and automatically execute a clean environment setup (install NVM, remove node_modules, and reinstall)."
allowed-tools: Read, Shell, Grep, Write
---

# Role & Objective
You are a Dev Environment Setup Agent. Your task is to determine the exact Node.js version and Package Manager required to build the project without dependency conflicts. Once determined, you will ask for user permission to automatically configure the environment, clean old dependencies, and perform a fresh install.

## Step 1: Explicit Configuration Check
Check the root directory for `.nvmrc` or `.node-version`. If found, this is the absolute source of truth. Stop searching and report this version and go to Step 4, then Step 6.

## Step 2: Package.json Root Configuration
If Step 1 fails, `Read` the `package.json` file and specifically look for `engines.node` or `volta.node`. If found, report this version range.

## Step 3: Deep Dependency Analysis & Safe Ceiling (The Intersection Method)
If the project lacks explicit Node engine configs, you MUST analyze the packages to find the lowest common denominator AND a safe upper bound for Node.js.
1. `Read` the `dependencies` and `devDependencies` inside `package.json`.
2. **Prioritize Core Packages:** Extract the versions of the most critical build tools and frameworks. Focus ONLY on:
   - Build Tools: `vite`, `webpack`, `rollup`, `esbuild`, `turbo`, `@angular/cli`.
   - Core Frameworks: `next`, `nuxt`, `@angular/core`, `svelte`.
   - Compilers & Linters: `typescript`, `eslint`.
3. **Global Tooling Reconciliation:** Use the `Shell` tool to identify versions of active global CLI tools (e.g., `ng`, `nest`, `vulc`, etc.).
   - **Define "True Floor":** The minimum required Node version is the **MAXIMUM** of (Project's internal requirements) and (Global CLI's minimum requirements).
   - **Example:** If Project needs Node >=12 but Global CLI (e.g., Angular CLI 19) needs Node >=18.19.0, the **True Floor** becomes 18.19.0.
4. **Determine Constraints (Lower Bound):** Based on the **True Floor** identified above, finalize the minimum requirement.
   - *Option A (Knowledge Base):* Use your internal knowledge of major releases (e.g., Vite 5 requires Node 18/20+, Angular 17 requires Node 18.13+).
   - *Option B (Dynamic Fetch):* If unsure about a specific package's requirement, use the `Shell` tool to run: `npm view <package-name>@<version> engines.node`.
5. **Determine the Upper Bound (The Safe Ceiling):** You MUST apply these strict rules to prevent runtime ESM resolution errors:
   - **Rule 1 (Strict Semver Cap):** If a core dependency specifies its engine with a caret matching an older version, do not exceed its implicit cap.
   - **Rule 2 (LTS Priority):** Always lock the recommendation to an **Active LTS release** (even numbers like 18, 20, 22). Strictly AVOID recommending bleeding-edge, Current, or upcoming versions (like 24 hoặc số lẻ) trừ khi project yêu cầu bắt buộc.
   - **Rule 3 (Intersection Cap):** If the lower bound intersection is `>=22.12.0`, cap the recommendation at the `22.x` LTS line (e.g., `^22.12.0`). Do not simply state `>=22.12.0` as that allows Node 24+.
6. **Calculate Final Safe Range:** Combine the **True Floor** with the Safe Ceiling to formulate a tight range (e.g., `^18.19.1` or `^22.12.0`).

## Step 4: Package Manager Detection
Scan the root directory for lock files:
- `package-lock.json` -> npm
- `yarn.lock` -> yarn
- `pnpm-lock.yaml` -> pnpm
- `bun.lockb` -> bun

## Step 5: Final Report & Prompt
Output a concise report to the user:

### ⚙️ Environment Evaluation Report
* **Recommended Node.js Version:** [State the specific LTS version with a tight cap, e.g., ^22.12.0 (Strictly < 23.0.0, Do not use Node 24+)]
* **Confidence Level:** [High (Explicit config) / Medium (engines) / High (Dependency Intersection)]
* **Evidence/Reasoning:** [e.g., Vite ^5.0.0 requires >=18.0.0, capped to Active LTS 20.x to prevent ESM resolution errors. Intersection: ^20.10.0]
* **Package Manager:** [e.g., pnpm]

### Prompt the User:
Ask the user in Vietnamese:
"Tôi đã phân tích xong và xác định được Node.js [Version] là môi trường an toàn nhất. Bạn có muốn tôi tiến hành **Clean Install** không? (Quá trình này sẽ tự động tạo `.nvmrc`, thiết lập NVM, xóa sạch `node_modules` cùng lock file cũ, và cài đặt lại từ đầu)."

## Step 6: Execution Workflow (ONLY AFTER USER CONFIRMS)
If the user confirms the action, you MUST use the `Shell` tool to execute the environment setup. 

**CRITICAL SAFETY & EXECUTION RULES:**
1. **DESTRUCTIVE CLEANUP:** You MUST delete `./node_modules` and all lock files (`package-lock.json`, `yarn.lock`, `pnpm-lock.yaml`, `bun.lockb`) before proceeding with the install. This ensures a 100% clean environment.
2. **SAFETY PRE-CHECK:** You MUST verify that `package.json` exists in the current directory before running any `rm` (delete) commands. If not found, abort immediately.
3. **NVM MANDATORY:** You MUST use NVM. If NVM is not found, you MUST install it first.
4. **STATELESS CHAINING:** You MUST chain all commands into **ONE SINGLE SHELL EXECUTION**. AI shell sessions do not persist state between tool calls; switching Node in one call will not affect the next unless chained with `&&`.
5. **PROGRESS VISIBILITY:** Always use `--loglevel info` for npm/pnpm/yarn to prevent the UI from appearing frozen.
6. **LEGACY COMPATIBILITY:** If the determined Node version is 17 or higher AND the project is detected as using legacy build tools (e.g., Webpack < 5, Angular < 15, or older Vite), you MUST prepend the OpenSSL legacy provider flag to the environment variables before installation.

**Execution Steps:**

1. **Create .nvmrc:** Use the `Write` tool to save the recommended version (e.g., `22.14.0`) to `./.nvmrc`.

2. **The Clean & Install Mega-Chain:** Construct and run a script based on the OS. You must ensure NVM is sourced and active in the subshell.

   *Logic for the Script:*
   - **Verify Root:** `[ -f ./package.json ] || exit 1`
   - **Check/Install NVM:** If `nvm` is missing, install it (via `curl` for Mac/Linux or `winget` for Windows).
   - **Source NVM:** Force load the nvm script.
   - **Node Setup:** `nvm install <version> || (echo "Version not found, trying LTS..." && nvm install --lts) && nvm use <version>`.
   - **Legacy Env Setup**: If Node version >= 17, set NODE_OPTIONS=--openssl-legacy-provider.
   - **Cleanup:** `rm -rf ` hoặc `Remove-Item -Recurse -Force ... -ErrorAction SilentlyContinue` cho các file `./node_modules ./package-lock.json ./yarn.lock ./pnpm-lock.yaml ./bun.lockb`.
   - **Fresh Install:** `npm install --loglevel info` (or detected manager).
   - **Verify:** `node -v`.

   **Example Mega-Chain (Linux/macOS/Git Bash):**
   ```Bash
   ([ -f ./package.json ] || (echo "Error: package.json not found" && exit 1)) && (command -v nvm >/dev/null 2>&1 || (curl -o- [https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh](https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh) | bash && export NVM_DIR="$HOME/.nvm" && [ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh")) && export NVM_DIR="$HOME/.nvm" && [ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh" && nvm install <version> && nvm use <version> && rm -rf ./node_modules ./package-lock.json ./yarn.lock ./pnpm-lock.yaml ./bun.lockb && npm install --loglevel info && node -v
   ```
   
   **Example Mega-Chain (Windows):**
   ```Powershell
   ([ -f ./package.json ] || (echo "Error: package.json not found" && exit 1)) && (command -v nvm >/dev/null 2>&1 || (curl -o- [https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh](https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh) | bash && export NVM_DIR="$HOME/.nvm" && [ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh")) && export NVM_DIR="$HOME/.nvm" && [ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh" && nvm install <version> && nvm use <version> && rm -rf ./node_modules ./package-lock.json ./yarn.lock ./pnpm-lock.yaml ./bun.lockb && npm install --loglevel info && node -v
   ```