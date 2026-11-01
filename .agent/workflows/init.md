---
description: First step when starting to work on a new or existing project. Scans the codebase, analyzes components and context to generate or update Agents.md with an overview of the project.
---

# Workflow Project Initialization & Overview (/init or /overview)

This command helps the user generate a comprehensive overview of the current project, storing it in `Agents.md`. It provides the AI with critical context for future sessions.

**FOLLOW THIS PROCESS IMMEDIATELY AFTER THE USER CALLS THE WORKFLOW:**

**Step 1: Intelligent Scanning**

- Silently scan the project directory tree structure. **CRITICAL:** Do NOT scan or list contents of large generated directories (e.g., `node_modules`, `build`, `dist`, `target`, `.git`, `.idea`, `vena`).
- Identify and read the contents of core project files. Examples include:
  - Root `README.md`
  - Package managers/build scripts: `package.json`, `pom.xml`, `build.gradle`, `requirements.txt`, `go.mod`.
  - Key configuration files: `.env.example`, `application.yml`, `docker-compose.yml`.
- If the project lacks clear documentation or standard files, ask the user 1-2 clarifying questions about the project's purpose or tech stack before proceeding.
- PAUSE and wait for the user's response if questions were asked. Otherwise, proceed to Step 2.

**Step 2: Analysis & Drafting**

- Based on the scan in Step 1, draft a comprehensive overview of the project in memory.
- The overview MUST include the following sections (if applicable):
  1.  **Business Goal / Purpose:** What does this project do? Who is it for?
  2.  **Tech Stack:** Languages, frameworks, databases, core libraries.
  3.  **High-Level Architecture:** Key modules, components, and how they interact.
  4.  **Core Features:** A brief bulleted list of the main functionalities.
  5.  **Project Conventions:** Any specific coding style, folder structure rules, or testing approaches observed.
- **CRITICAL:** Keep the entire draft concise. The final document MUST BE LESS THAN 200 lines.

**Step 3: Review & Finalize `Agents.md`**

- Check if an `Agents.md` file already exists in the project root.
  - **If `Agents.md` DOES NOT exist:** Present the drafted overview to the user. Ask: "I have drafted the project overview. Would you like me to create `Agents.md` with this content, or are there any corrections you'd like to make?"
  - **If `Agents.md` ALREADY exists:** Compare the new draft with the existing content. Present a summary of the _differences_ (what's new, what's changed) to the user. Ask: "I have found new information. Here is what I propose to update in `Agents.md`. Do you approve these changes?"
- DO NOT WRITE the file until the user explicitly confirms or provides corrections.
- Once confirmed, create or overwrite `Agents.md` with the final content.
