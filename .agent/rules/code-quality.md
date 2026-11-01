---
trigger: always_on
---

# Code Quality Guidelines

## Naming & Structure

- **Intention-revealing names** — variables, functions, and classes must say *what* they do, not *how*; avoid abbreviations (`usrCnt` → `userCount`)
- **Consistent conventions** — follow the naming style already in the codebase; don't mix styles within a file
- **Function length** — a function should do one thing; if it needs a comment to explain a section, that section deserves its own function
- **Nesting depth** — max 3 levels of nesting; use early returns (guard clauses) to flatten logic
- **File cohesion** — a file should have one clear responsibility; if the filename contains "and", it probably does too much

## SOLID Principles

- **Single Responsibility** — a class/module has one reason to change; split when responsibilities diverge
- **Open/Closed** — extend behavior via new code (plugins, strategies, subclasses), not by modifying existing logic
- **Liskov Substitution** — subclasses must be fully substitutable for their parent; don't override to throw or no-op
- **Interface Segregation** — prefer small, focused interfaces over one large general-purpose one; callers shouldn't depend on methods they don't use
- **Dependency Inversion** — depend on abstractions, not concrete implementations; inject dependencies rather than instantiating them inside

## Testing

- **Test behavior, not implementation** — tests should survive refactoring; if renaming a private method breaks a test, the test is wrong
- **AAA structure** — every test: Arrange → Act → Assert, one assert per logical outcome
- **No skipped or commented-out tests** — a skipped test is a lie; delete it or fix it
- **Test naming** — names describe the scenario: `should_return_404_when_user_not_found`, not `test1`
- **No logic in tests** — avoid loops, conditionals, and try/catch inside tests; each case gets its own test
- **Cover the boundaries** — null/empty/zero, max values, concurrent access, and error paths are the first things to test, not the last

## Code Smells — Stop and Fix

- **Magic numbers/strings** — extract to named constants; `86400` tells nothing, `SECONDS_IN_A_DAY` does
- **Dead code** — remove unused variables, functions, imports, and feature flags; version control preserves history
- **Duplicated logic** — three identical blocks is one too many; extract to a shared function
- **God class/function** — if it's over ~200 lines and touches everything, break it up
- **Primitive obsession** — don't pass 5 raw strings where a value object would clarify intent
- **Long parameter list** — more than 3-4 parameters usually signals a missing abstraction; group into an object

## Code Quality Response Protocol

When a quality issue is found:
1. **Name the smell** — identify specifically what the problem is before proposing a fix
2. **Fix at the right scope** — a naming issue doesn't require restructuring; a god class does
3. **Don't over-engineer the fix** — the goal is clarity, not demonstrating patterns
