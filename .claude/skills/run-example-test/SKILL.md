---
name: run-example-test
description: Run a single distribution example or tutorial integration test (IT) fast, instead of the whole ~6 min example suite. Use when asked to run, verify, or check one example/tutorial test in the api-gateway distribution module.
---

# Run Example Test

Runs **one** example/tutorial integration test from the `distribution` module in
~2 seconds, bypassing the hardcoded failsafe suite that would otherwise run the
entire example suite (~6 min, some tests need internet).

## When to use

- The user wants to run / verify / check a specific example or tutorial test,
  e.g. `ChainExampleTest`, `JwtVerificationExampleTest`, a `*ExampleTest` under
  `com.predic8.membrane.examples.*`, or a `*TutorialTest` under
  `com.predic8.membrane.tutorials.*`.

## The distribution must be built first

The base classes (`DistributionExtractingTestcase`) **unzip
`distribution/target/membrane-api-gateway-*.zip` and start membrane from the
unzipped distribution — not from the source tree.** So the tests only ever see
what was last built. Before running them the distribution must be built at the
repo root with:

```bash
mvn clean install -DskipTests
```

The script does this for you (see below). `-DskipTests` still compiles tests but
skips running them, which also dodges the German-locale `annot` test failure on
this machine.

## How to run

Invoke the helper script with the test class (simple name, fully-qualified name,
or path all work):

```bash
.claude/skills/run-example-test/run-example-test.sh ChainExampleTest      # fast, ~2s
.claude/skills/run-example-test/run-example-test.sh -b ChainExampleTest   # rebuild dist first, then run
```

With **no argument** it runs the full example IT suite via failsafe (slow, some
tests need internet) — and **always rebuilds the distribution first**, because
the suite runs from the built distribution.

The script:
1. **Distribution build.** For the full suite it **always** runs
   `mvn clean install -DskipTests` at the root. For a single test it builds the
   `.zip` only if it is missing, or if you pass `-b`.
2. Resolves the class name to its FQN by finding the `.java` under
   `distribution/src/test/java`.
3. Incrementally recompiles distribution test classes (offline) so edits to the
   test class are picked up.
4. Runs just that class via a tiny JUnit Platform launcher (`RunOne.java`), with
   the working directory set to `distribution/` (the tests resolve `./target`).

Exit code is non-zero if the test fails or no tests were found.

**When to pass `-b` (single test):** the test recompile in step 3 only covers the
test class itself. Anything that lives *inside* the distribution `.zip` — the
example's config/YAML/scripts, or any upstream module like `core` — is **not**
reflected until the distribution is rebuilt. Pass `-b` (or delete the zip) after
editing those.

## Locale

The tests run under the **English** locale, so output is deterministic on a
German JVM (this machine defaults to `de`/`DE`). On macOS the JVM ignores
`LANG`/`LC_ALL`, so the script uses `-Duser.language=en -Duser.country=US`
instead, applied to all three JVMs involved:

- the JUnit launcher / failsafe fork — passed directly (`-DargLine` for failsafe);
- the **child membrane process** the tests start via `membrane.sh` — via exported
  `JAVA_OPTS` (`start_router.sh` forwards it to `java`, and `Process2` copies the
  environment into the child).

(A test that overrides `getEnvs()` with its own `JAVA_OPTS`, e.g.
`LoggingJsonExampleTest`, replaces it and won't get the locale — a rare edge case.)

## Why a launcher instead of `-Dit.test`

`distribution/pom.xml` hardcodes `<test>**/ExampleTests.java</test>` for failsafe,
and `ExampleTests` is a JUnit Platform `@Suite`. The suite engine ignores
failsafe's class filter, so `-Dit.test=Foo` runs the **whole** suite anyway. The
launcher selects a single class directly and skips the suite.

## Gotchas

- **Changed `core` (or another upstream module), not just the test?** Step 3 only
  recompiles `distribution` test classes. Rebuild first with
  `mvn clean install -DskipTests` from the repo root so the `.zip` reflects your
  change, or delete the zip and let the script rebuild it.
- A few suite tests are environmentally flaky / need internet (e.g. anything
  hitting `api.predic8.de`). Prefer the offline tests under
  `com.predic8.membrane.examples.withoutinternet` when demonstrating.
- Cached artifacts (`target/cp.txt`, `target/run-one/`) live under `target/` and
  are recreated automatically; `mvn clean` wipes them harmlessly.
