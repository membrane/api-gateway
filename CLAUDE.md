# Project instructions

## Java code conventions

When writing or editing **any** Java in this project (production code and tests), follow the
`code-conventions` skill at `.claude/skills/code-conventions/SKILL.md`. Apply it even when the skill
was not explicitly invoked.

Key points:
- Target **Java 21**; use modern language features where they improve readability.
- Prefer **`var`** for local variables. The team uses IntelliJ, whose inlay hints show the inferred
  type, so `var` is preferred even when the type is not obvious from the right-hand side.
- Avoid single-use variables that don't improve clarity.
