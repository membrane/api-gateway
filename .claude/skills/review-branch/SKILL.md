---
name: review-branch
description: Review the current git branch against master — code quality, refactoring opportunities, regressions, correctness, and test coverage — and print a severity-grouped markdown report. Use whenever the user asks to review the branch, review their changes against master, do a pre-PR / pre-merge review, or asks "is this branch ready", "what's wrong with my changes", "review what I've done so far". Builds and runs the affected module's tests to catch real regressions. This is the whole-branch counterpart to the built-in /code-review (which only looks at the current diff).
---

# Review Branch

Review everything that changed on the current branch relative to `master` and report what
needs attention before the branch is merged. The goal is a review a senior engineer on this
project would give: focused on real problems, grounded in the actual code, and ranked so the
author knows what to fix first.

## Scope of the review

Review **committed changes on the branch plus any uncommitted working-tree changes**, all
relative to the merge-base with `master`. Concretely:

```bash
BASE=$(git merge-base HEAD master)
git diff --stat $BASE            # committed + uncommitted, names only
git diff $BASE                   # the full diff to review
```

`git diff $BASE` (no `--cached`, no second ref) compares the working tree against the
merge-base, so it already includes both committed and uncommitted changes — which is exactly
the scope we want. Don't review changes that are also on `master`; the merge-base keeps those
out.

**Untracked (newly created, not yet `git add`-ed) files are out of scope** — `git diff` does
not show them. This is deliberate: review what's in version control. If the author wants a brand
new file reviewed, they should `git add` it first. If `git status --porcelain` shows untracked
files that look relevant to the change, mention them in one line so the author can stage them and
re-run — but don't review their contents.

If the branch *is* `master`, or there is no diff, say so and stop — there is nothing to review.

## How to work

Review in four passes. Don't just walk the diff top to bottom — read enough of the surrounding
code to understand what the change is *for*, then judge it. A diff hunk that looks fine in
isolation can be a regression once you see its callers.

1. **Understand the change.** Read the diff and the commit messages (`git log --oneline $BASE..HEAD`).
   What is this branch trying to do? Hold that intent in mind — most findings are "this doesn't
   actually achieve the intent" or "this achieves it but breaks something else".

2. **Correctness & regressions.** This is the most important pass. For each change, ask what
   existing behavior it could break: callers that relied on the old contract, edge cases
   (null/empty/large input), changed defaults, altered exception or HTTP-status behavior,
   thread-safety, resource leaks. When a public method's behavior changes, grep for its callers
   rather than assuming the change is local. Then **build and run the affected tests** (see below)
   — static reasoning finds the suspicious spots, the tests confirm them.

3. **Code quality.** Apply the project's `code-conventions` skill
   (`.claude/skills/code-conventions/SKILL.md`) — Java 21, `var`, no needless intermediate
   variables, text blocks, `formatted` over concatenation, early returns, braces. Flag only real
   deviations in the changed code, not pre-existing style elsewhere.

4. **Refactoring & test coverage.** Look for duplication the change introduced or could now
   remove, methods that got too long, abstractions that would simplify the change, and whether
   new behavior is actually covered by a test. For a Membrane interceptor/plugin, check that new
   config attributes are documented and tested the way the rest of the codebase does it; the
   `find-interceptor-impl` and `find-example` skills help locate the conventions to match.

## Building and running tests

Regressions are confirmed by running tests, not by reading code alone. Run the tests for the
**modules that actually changed**, not the whole build. The modules are `annot`, `core`,
`distribution`, `war`, `test`.

Map the changed files to a module from their path (`core/src/...` → `core`), then:

```bash
# Compile + run one module's tests under the English locale (this machine defaults to de/DE,
# which makes locale-sensitive assertions fail spuriously).
mvn -pl core test -Duser.language=en -Duser.country=US
```

Repo-specific gotchas that will otherwise waste time or produce misleading failures:

- **`core`: `-Dtest=SomeClass` does not isolate a single class.** `core`'s `UnitTests` is a JUnit
  Platform `@Suite` that runs the whole package regardless of the `-Dtest` filter. To run one
  class in isolation, use the JUnit Platform launcher directly rather than the failsafe/surefire
  filter. For a quick regression check it's usually fine (and simpler) to run the whole `core`
  module suite.
- **Don't use `-am` to pull in upstream modules.** It drags in the `annot` module, whose
  annotation-processor test fails on a German-locale JVM and has nothing to do with the branch
  under review. Build/test the changed module directly instead.
- **Changes under `distribution/` (examples & tutorials)** are verified by integration tests that
  unzip the *built* distribution, not the source tree. Use the `run-example-test` skill
  (`.claude/skills/run-example-test/SKILL.md`) — it rebuilds the distribution when needed, runs a
  single `*ExampleTest` / `*TutorialTest` fast, and already sets the English locale. Don't try to
  run these with a plain `mvn test`.

If a relevant test suite is too slow to run in full, run the most relevant classes and **say so in
the report** — list the suites you ran and the ones you judged too expensive to run, so the author
can finish the job. Never claim tests pass without having run them.

## Report format

Print the report as markdown in the conversation. Lead with a one-line verdict so the author
immediately knows whether the branch is in good shape, then the findings ranked by severity.

```markdown
# Branch review: <branch> vs master

**Verdict:** <one line — e.g. "Solid, two blockers to fix before merge" / "Ready to merge" / "Needs rework">

<2–4 sentences: what the branch does and the overall shape of the change.>

## Build & tests
- `mvn -pl core test` → <pass/fail, counts>. <Anything notable.>
- Not run: <suites skipped and why.>

## Blockers
Things that are wrong or will break — bugs, regressions, missing coverage for risky changes.
- **`core/.../Foo.java:42` — <short title>.** <What's wrong and why it matters; the failing
  case or broken caller.> *Fix:* <concrete suggestion.>

## Improvements
Should-fix-but-not-blocking — refactoring, quality, weaker test coverage.
- **`core/.../Bar.java:88` — <short title>.** <What and why.> *Fix:* <suggestion.>

## Nitpicks
Minor style / convention points, grouped tersely. Skip this section if there's nothing real.
```

Rules that keep the report useful:

- **Cite `file:line`** for every finding so the author can jump straight to it.
- **Rank honestly.** A misplaced `var` is not a blocker; a regression is. If the branch is clean,
  say "Ready to merge" and don't manufacture findings to fill sections — an empty Improvements
  section is a fine outcome.
- **Explain the why, propose a fix.** "This is wrong" is not actionable; "this NPEs when the
  header is absent because `getFirst` returns null — guard with X" is.
- **Stay in scope.** Review what the branch changed. Pre-existing problems in untouched code are
  out of scope unless the change makes them materially worse or newly reachable.
