---
name: brainstorming
description: "You MUST use this before any creative work - creating features, building components, adding functionality, or modifying behavior. Explores user intent, requirements and design before implementation." # Modified version of superpower:brainstorming
allowed-tools: Read, Glob, Grep, Bash
---

<objective name="collaborative_to_clarify_requirements">
Help turn ideas into fully formed designs and specs through natural collaborative dialogue. 
Start by understanding the current project context, then ask questions one at a time to refine the idea.
</objective>

<constraint name="no_implementation_before_approval">
Do NOT invoke any implementation skill, write any code, scaffold any project, or take any implementation action until you have presented a design/spec and the user has approved it. This applies to EVERY project regardless of perceived simplicity.
</constraint>


<anti_pattern name="avoid_jumping_to_implementation">
Every project goes through this process. A todo list, a single-function utility, a config change — all of them. "Simple" projects are where unexamined assumptions cause the most wasted work. The design/spec can be short (a few sentences for truly simple projects), but you MUST present it and get approval.

| Anti-Pattern | Why |
|--------------|-----|
| Jumping to solutions before understanding | Wastes time on wrong problem |
| Assuming requirements without asking | Creates wrong output |
| Over-engineering first version | Delays value delivery |
| Ignoring constraints | Creates unusable solutions |
| "I think" phrases | Uncertainty → Ask instead |
</anti_pattern>

<process_flow>
```
Explore project context
        ↓
Ask clarifying questions
        ↓
Scan existing codebase for related logic
        ↓
Propose 2-3 approaches (reuse-first if existing logic found)
        ↓
Present design/spec sections
        ↓
User approves design/spec?
   ├── no  → revise → Present design/spec sections
   └── yes → Write design/spec doc
                  ↓
        Invoke writing-plans skill  ← TERMINAL STATE
```

The terminal state is invoking `writing-plans`. Do NOT invoke any other implementation skill. The ONLY skill you invoke after brainstorming is `writing-plans`.
</process_flow>

<process>

  <step name="understand_the_idea">
    <instructions>
      - Check out the current project state first (files, docs, recent commits)
      - Ask questions one at a time to refine the idea
      - Prefer multiple choice questions when possible, but open-ended is fine too
      - Only one question per message — if a topic needs more exploration, break it into multiple questions
      - Focus on understanding: purpose, constraints, success criteria
      - **UI features:** If the feature involves UI/frontend, scan for existing pages/components
        similar to what's being built. Read 2-3 related pages to understand layout structure,
        form patterns, navigation flow, and state management. Present findings:
        "Found [N] related pages: [list]. Should the new feature follow the same pattern?"
    </instructions>
  </step>

  <step name="scan_existing_codebase">
    <instructions>
      BEFORE proposing approaches, scan the codebase for existing logic related to
      the feature being discussed. This prevents re-implementing what already exists.

      1. Use Grep/Glob to find similar: function names, service methods, patterns,
         modules that handle related business logic
      2. Read the most relevant matches to understand their scope and interface
      3. If similar logic is found, present to user:
         "Found existing logic at [path] that does [similar thing].
          Its interface expects [inputs] and returns [outputs].
          Options:
          1. Extend/reuse this existing logic
          2. Create new implementation (justify why existing doesn't fit)
          3. Refactor both into a shared abstraction"
      4. If NO similar logic found, note: "No existing related logic found — proceeding
         with fresh design."

      This step is MANDATORY — do not skip even if the feature seems obviously new.
      Existing code may solve 80% of the problem.
    </instructions>
  </step>

  <step name="explore_approaches">
    <instructions>
      - If scan_existing_codebase found related logic, Approach 1 MUST be
        "Reuse/extend existing code" with specifics on what to reuse and what to add.
        Only propose "create new" as an alternative with clear justification for
        why the existing logic is insufficient.
      - If no existing logic was found, propose 2-3 different approaches with trade-offs
      - Present options conversationally with your recommendation and reasoning
      - Lead with your recommended option and explain why
    </instructions>
  </step>

  <step name="present_design">
    <instructions>
      - Once you believe you understand what you're building, present the design
      - Scale each section to its complexity: a few sentences if straightforward, up to 200-300 words if nuanced
      - Ask after each section whether it looks right so far
      - Cover: architecture, components, data flow, error handling, testing
      - Be ready to go back and clarify if something doesn't make sense
    </instructions>
  </step>

  <step name="write_design_doc">
    <instructions>
      - **Folder naming:** Before writing, scan `docs/plans/` for existing folders related
        to this feature/module. If a related folder exists, place the new spec there
        (append date-prefixed file). If no related folder exists, create one with a
        canonical module/feature name (noun-based, kebab-case — e.g., `function-a/`,
        NOT action-based like `initiate-function-a/`). If ambiguous, ask user to confirm
        the canonical folder name.
      - Write the validated design/spec to `docs/plans/<topic>/YYYY-MM-DD-spec.md`
      - Use `elements-of-style:writing-clearly-and-concisely` skill if available
      - ⛔ STOP — Present the spec document to user and ask: "Spec document written. Commit to git?"
        - If approved → Commit the design/spec document to git
        - If declined → Continue without committing (user may want to review or edit first)
    </instructions>
  </step>

  <step name="invoke_writing_plans">
    <instructions>
      - Invoke the `writing-plans` skill to create a detailed implementation plan if available
      - Fallback to built-in plan skill if `writing-plans` is not available
      - Do NOT invoke any other skill
    </instructions>
  </step>

</process>

<key_principles>
  <principle name="one_question_at_a_time">Don't overwhelm with multiple questions — ask one, wait for response, then ask the next.</principle>
  <principle name="prefer_multiple_choice">Multiple choice questions are easier to answer than open-ended ones whenever possible.</principle>
  <principle name="yagni">Remove unnecessary features from all designs ruthlessly. You Ain't Gonna Need It.</principle>
  <principle name="explore_alternatives">Always propose 2-3 approaches before settling on a design.</principle>
  <principle name="incremental_validation">Present design section by section, get approval before moving on.</principle>
  <principle name="be_flexible">Go back and clarify when something doesn't make sense — don't push forward with uncertainty.</principle>
</key_principles>

<examples>
  <example>
    <context>User asks: "Can you add a search feature to my app?"</context>
    <correct_behavior>
      1. Read existing codebase to understand project structure
      2. Ask: "What should users be able to search — posts, users, or both?"
      3. (After answer) Ask: "Should results appear as the user types, or only after submitting?"
      4. Propose approaches: client-side filter vs. backend search vs. Algolia integration
      5. Present design/spec and get approval
      6. Write design/spec doc, then invoke writing-plans
    </correct_behavior>
    <incorrect_behavior>
      Immediately writing search implementation code without asking any questions.
    </incorrect_behavior>
  </example>
  <example>
    <context>User asks: "Fix the bug in the login form"</context>
    <correct_behavior>
      1. Read the login form code to understand the current state
      2. Ask: "Can you describe what's happening vs. what you expect?"
      3. Propose a brief design/spec (even one sentence) explaining the fix approach
      4. Get approval, write a short doc entry, invoke writing-plans
    </correct_behavior>
    <incorrect_behavior>
      Treating this as "too simple to need a design/spec" and jumping straight to editing code.
    </incorrect_behavior>
  </example>
</examples>