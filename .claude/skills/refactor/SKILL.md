---
name: refactor
description: Refactor Java code in this repo without changing behavior. Not for bug fixes or new features.
---

# Refactor

Behavior-preserving restructuring, Fowler-style. The catalog below is the vocabulary; the
workflow is what keeps it safe.

## Workflow

1. **Pin behavior first.** Find the existing test class
   (`<module>/src/test/java/<mirrored package>/<ClassName>Test.java`). If the code you're about to
   move has no test covering it, write a characterization test against the *current* behavior
   before touching anything. If the code is untestable as-is, the first refactoring is the one
   that makes it testable (usually Extract Method / Extract Class), done in the smallest step you
   can verify by compiling.
2. **One refactoring per step.** Apply a single named transformation, then compile. Never mix two
   catalog entries in one edit, and never mix a refactoring with a behavior change — if you spot a
   bug mid-refactor, note it and finish the refactor first, then fix it as a separate change with
   its own failing test.
3. **Run the affected tests** after each step, not just at the end — use
   `test/scripts/run-core-test.sh <FQCN or package>` (see CLAUDE.md; `-Dtest=` does not isolate a
   class in `core`). Green after every step is the whole safety net.
4. **Report** at the end: which refactorings were applied to which methods, what is now testable
   that wasn't, and anything you deliberately left alone.

## Scope discipline

- Refactor only what the user named plus what that change strictly requires. Adjacent messy code
  stays messy — mention it, don't touch it (CLAUDE.md §3).
- Public API of `@MCElement`-annotated config classes is a contract: attribute/child setter names
  and signatures are the config grammar. Do not rename or re-sign them as part of a refactor.
  Private helpers behind them are fair game.
- Delete what your change orphaned (now-unused imports, fields, private methods). Leave
  pre-existing dead code alone.

## Catalog

### Extract Method — the default move
Pull out any block that has one responsibility and could be tested on its own. Signals: a comment
explaining what the next few lines do, a blank-line-separated paragraph inside a method, a loop
body doing real work, a nested conditional branch of more than ~3 lines.

- Name the method after **what it answers or produces**, not how — `isExpiredToken`,
  `resolveSchemaFor`, not `doCheck2`.
- Prefer extracting to a **pure private static** method (params in, value out, no field access) —
  that's the version you can unit-test directly and the repo's stated preference (CLAUDE.md:
  "prefer pure methods where practical").
- If the block reads three fields and writes none, pass them as parameters; if it writes two or
  more locals, the block wants Extract Class instead (below), not a method with out-params.
- Keep the extracted method small and cohesive; a helper that itself needs a section comment is
  not done being extracted.

### Replace Temp with Query / Inline Variable
A local that is assigned once from an expression and read later is usually a name looking for a
method. Replace it with a call to a small query method — that removes the temp *and* makes the
computation reachable from a test.

- Inline a temp outright only when the expression is short and used once.
- A variable that gets **reassigned** is the strongest extract signal in this repo: CLAUDE.md says
  reassignment means "extract a helper method instead". Convert accumulate-in-a-loop temps into a
  helper returning the value, and make the remaining locals `final`.
- Split Temporary Variable when one local holds two different things at two points; never reuse a
  name for a second purpose.

### Remove Duplication
Duplication first, abstraction second — extract the shared code only once you've seen it twice and
the two copies mean the same thing (same reason to change), not merely look alike.

- Identical code in **two methods of one class** → Extract Method, call it from both.
- Identical code in **two sibling subclasses** → Pull Up Method to the shared supertype.
- Same shape, different values → parameterize the extracted method. Same shape, different *step*
  → pass the varying step as a small functional interface or use Template Method — but only if
  there are ≥3 call sites, otherwise the indirection costs more than the duplication.
- Similar-looking code that would need to change for different reasons is **not** duplication.
  Leave it and say so.

### Introduce Abstractions — only when they pay
CLAUDE.md forbids speculative abstraction. Introduce one only when a concrete pressure exists now:

- **Extract Class** — a class holding two clusters of fields that don't talk to each other, or a
  method needing 4+ locals to survive extraction. Move the cluster and the methods that use it.
- **Replace Conditional with Polymorphism / sealed switch** — a `switch` or `if/else` chain on a
  type tag repeated in more than one method. This repo prefers pattern matching over sealed types
  and records to hand-rolled hierarchies (CLAUDE.md code style) — prefer a `switch` over a sealed
  interface before inventing an abstract base class.
- **Introduce Parameter Object / Value Object** — see long parameter lists below.
- **Replace Magic Literal with Constant** — a literal appearing twice, or once with non-obvious
  meaning.
- Do **not** introduce an interface with a single implementation, a factory for a constructor
  call, or a strategy for a two-branch conditional.

### Move Method — feature envy
A method that calls more methods/fields of another object than of its own belongs on that other
object. Move it, leaving a delegating method behind only if external callers need it.

- Extract the envious *part* first if only a portion is envious, then move that.
- Related smells: Move Field (a field used mainly by another class), Hide Delegate (callers doing
  `a.getB().getC().doIt()` — give `a` the method), Remove Middle Man (a class that only forwards).
- If the target class is one you must not change (a JDK/library type or a generated/config class),
  don't move — extract a static helper in a `util`-style class instead and note why.

### Long Parameter Lists
Threshold: **more than 3 parameters, or any two adjacent parameters of the same type** (call-site
transposition bugs).

- Parameters that always travel together and mean one thing → **Introduce Parameter Object**, as a
  `record` (immutable, matches the repo's immutable-by-default rule).
- A parameter derivable from another parameter → **Replace Parameter with Query**, drop it.
- A boolean flag that selects behavior → **Remove Flag Argument**: split into two clearly named
  methods.
- Several parameters that are all fields of the caller → the method probably wants to move to the
  caller's class (feature envy, above).
- Don't abbreviate parameter names in public interfaces (`docs/CONVENTIONS.md`).

## Repo-specific constraints

- Java 21. Prefer `record`s for parameter/value objects, pattern-matching `switch` over sealed
  types, `List.of`/`Collections.unmodifiableList` for extracted collection state,
  `getFirst()` over `.get(0)`.
- `final` on extracted fields, parameters, and locals wherever it compiles.
- SLF4J only; never introduce `System.out` while moving code.
- Don't reformat lines you didn't otherwise change — a diff full of whitespace hides the real
  refactoring.
- Every newly extracted method that has real behavior needs at least one test; add it to the
  existing mirrored test class rather than creating a new one (CLAUDE.md testing rules). Don't
  write tests for extracted accessors, `record` components, or generated `equals`/`hashCode`.

## When to stop

Stop when the named smell is gone. Resist the pull to keep going: a refactor that touches twice
the files the user expected is a worse outcome than one that leaves a second smell for later.
Name the leftovers in your report instead of fixing them.
